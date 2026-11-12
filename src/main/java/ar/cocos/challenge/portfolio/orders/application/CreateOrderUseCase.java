package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.orders.domain.AccountResources;
import ar.cocos.challenge.portfolio.orders.domain.CashAmount;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyKey;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyRepository;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import ar.cocos.challenge.portfolio.orders.domain.RejectionReason;
import ar.cocos.challenge.portfolio.orders.domain.ResourceCalculator;
import ar.cocos.challenge.portfolio.orders.domain.ShareQuantity;
import ar.cocos.challenge.portfolio.shared.application.ClockProvider;
import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.ConflictException;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import ar.cocos.challenge.portfolio.shared.domain.MissingAccountHeaderException;
import ar.cocos.challenge.portfolio.shared.domain.ResourceNotFoundException;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de creacion de ordenes y transferencias.
 *
 * <p>El flujo completo se ejecuta dentro de una unica transaccion:</p>
 * <ol>
 *   <li>Se toma el bloqueo pesimista de la cuenta, que serializa las
 *       operaciones concurrentes de esa cuenta (research R-004).</li>
 *   <li>Se resuelve la idempotencia: si la clave ya se uso con el mismo
 *       contenido se devuelve el resultado original; con contenido distinto se
 *       responde un conflicto (spec FR-057, FR-058).</li>
 *   <li>Se validan las reglas del request (errores de solicitud, sin
 *       persistir orden).</li>
 *   <li>Se resuelven el precio y la cantidad.</li>
 *   <li>Se validan los recursos disponibles; si no alcanzan, la orden se
 *       persiste como {@code REJECTED} con su motivo (spec FR-042).</li>
 *   <li>Se persisten la orden y su registro de idempotencia de forma
 *       atomica (spec FR-060).</li>
 * </ol>
 */
@Service
public class CreateOrderUseCase {

	private final AccountRepository accountRepository;
	private final InstrumentRepository instrumentRepository;
	private final OrderRepository orderRepository;
	private final IdempotencyRepository idempotencyRepository;
	private final ResourceCalculator resourceCalculator;
	private final OrderValidator orderValidator;
	private final OrderPricingPolicy pricingPolicy;
	private final RequestHasher requestHasher;
	private final ClockProvider clockProvider;

	/**
	 * @param accountRepository repositorio de cuentas
	 * @param instrumentRepository repositorio de instrumentos
	 * @param orderRepository repositorio de ordenes
	 * @param idempotencyRepository repositorio de idempotencia
	 * @param resourceCalculator calculadora de recursos disponibles
	 * @param orderValidator validador del request
	 * @param pricingPolicy politica de precios
	 * @param requestHasher calculadora de la huella del request
	 * @param clockProvider fuente de la hora UTC
	 */
	public CreateOrderUseCase(AccountRepository accountRepository, InstrumentRepository instrumentRepository,
			OrderRepository orderRepository, IdempotencyRepository idempotencyRepository,
			ResourceCalculator resourceCalculator, OrderValidator orderValidator, OrderPricingPolicy pricingPolicy,
			RequestHasher requestHasher, ClockProvider clockProvider) {
		this.accountRepository = accountRepository;
		this.instrumentRepository = instrumentRepository;
		this.orderRepository = orderRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.resourceCalculator = resourceCalculator;
		this.orderValidator = orderValidator;
		this.pricingPolicy = pricingPolicy;
		this.requestHasher = requestHasher;
		this.clockProvider = clockProvider;
	}

	/**
	 * Crea una orden o transferencia de efectivo.
	 *
	 * @param accountNumber numero de cuenta de la cabecera {@code X-AccountNumber}
	 * @param idempotencyKey clave de idempotencia de la cabecera
	 * @param request solicitud de creacion
	 * @return la orden creada, ejecutada, pendiente o rechazada
	 */
	@Transactional
	public Order create(String accountNumber, String idempotencyKey, CreateOrderRequest request) {
		orderValidator.validate(request);

		// La cabecera de cuenta ausente es un error de solicitud (400), no una
		// cuenta inexistente (404). Se valida antes de consultar la base.
		if (accountNumber == null || accountNumber.isBlank()) {
			throw new MissingAccountHeaderException("Missing required header 'X-AccountNumber'");
		}

		// El lock de la cuenta serializa las operaciones concurrentes. Se toma
		// ANTES de leer los saldos, para que el agregado incluya las ordenes
		// confirmadas por el escritor anterior (research R-004).
		Account account = accountRepository.findByAccountNumberForUpdate(accountNumber)
				.orElseThrow(() -> new AccountNotFoundException(accountNumber));
		String requestHash = requestHasher.hash(request);
		Optional<IdempotencyKey> existingKey = idempotencyRepository.findByAccountIdAndKey(account.id(),
				idempotencyKey);
		if (existingKey.isPresent()) {
			return replay(existingKey.get(), requestHash);
		}

		Instrument instrument = instrumentRepository.findByTicker(request.getTicker())
				.orElseThrow(() -> new ResourceNotFoundException("Instrument not found: " + request.getTicker()));
		orderValidator.validateInstrument(request, instrument);

		LocalDateTime now = clockProvider.now();
		Order order = buildOrder(account, instrument, request, now);

		Order persisted = orderRepository.save(order);
		idempotencyRepository.save(new IdempotencyKey(null, account.id(), idempotencyKey, requestHash,
				persisted.getId(), now));
		return persisted;
	}

	private Order replay(IdempotencyKey existingKey, String requestHash) {
		if (!existingKey.requestHash().equals(requestHash)) {
			throw new ConflictException("Idempotency key already used with a different request");
		}
		return orderRepository.findById(existingKey.orderId())
				.orElseThrow(() -> new IllegalStateException("Order for idempotency key not found"));
	}

	private Order buildOrder(Account account, Instrument instrument, CreateOrderRequest request, LocalDateTime now) {
		if (request.getSide().isTransfer()) {
			return buildTransfer(account, instrument, request, now);
		}
		return buildTrade(account, instrument, request, now);
	}

	private Order buildTransfer(Account account, Instrument instrument, CreateOrderRequest request,
			LocalDateTime now) {
		CashAmount amount = new CashAmount(request.getSize());
		if (request.getSide() == OrderSide.CASH_OUT) {
			AccountResources resources = resourceCalculator.calculateCash(account.id());
			if (amount.value().compareTo(resources.availableCash()) > 0) {
				return Order.rejected(account, instrument, request.getSide(), amount.value(), null, OrderType.MARKET,
						RejectionReason.INSUFFICIENT_CASH, now);
			}
		}
		return Order.transfer(account, instrument, request.getSide(), amount, now);
	}

	private Order buildTrade(Account account, Instrument instrument, CreateOrderRequest request, LocalDateTime now) {
		BigDecimal price;
		if (request.getType() == OrderType.MARKET) {
			Optional<BigDecimal> marketPrice = pricingPolicy.resolveMarketPrice(instrument);
			if (marketPrice.isEmpty()) {
				// Sin cotizacion no hay precio de ejecucion: la orden se persiste
				// como rechazada, sin tocar los recursos de la cuenta (spec FR-028).
				return Order.rejected(account, instrument, request.getSide(), requestedSize(request), null,
						OrderType.MARKET, RejectionReason.NO_MARKET_DATA, now);
			}
			price = marketPrice.get();
		} else {
			price = pricingPolicy.resolveLimitPrice(request.getPrice());
		}

		ShareQuantity quantity = resolveQuantity(request, price);
		if (quantity.isZero()) {
			// El importe no alcanza para una sola accion. Se persiste el importe
			// solicitado, que es el dato conhecido de la operacion: la columna
			// size exige un valor positivo (spec FR-032).
			return Order.rejected(account, instrument, request.getSide(), request.getAmount(), price,
					request.getType(), RejectionReason.AMOUNT_TOO_SMALL, now);
		}

		BigDecimal cost = quantity.value().multiply(price);
		if (request.getSide() == OrderSide.BUY) {
			AccountResources resources = resourceCalculator.calculateCash(account.id());
			if (cost.compareTo(resources.availableCash()) > 0) {
				return Order.rejected(account, instrument, request.getSide(), quantity.value(), price,
						request.getType(), RejectionReason.INSUFFICIENT_CASH, now);
			}
		} else {
			BigDecimal availableShares = resourceCalculator.calculateAvailableShares(account.id(), instrument);
			if (quantity.value().compareTo(availableShares) > 0) {
				return Order.rejected(account, instrument, request.getSide(), quantity.value(), price,
						request.getType(), RejectionReason.INSUFFICIENT_SHARES, now);
			}
		}

		if (request.getType() == OrderType.MARKET) {
			return Order.marketTrade(account, instrument, request.getSide(), quantity, price, now);
		}
		return Order.limitTrade(account, instrument, request.getSide(), quantity, price, now);
	}

	private ShareQuantity resolveQuantity(CreateOrderRequest request, BigDecimal price) {
		if (request.getAmount() == null) {
			orderValidator.validateWholeQuantity(request.getSize());
			return new ShareQuantity(request.getSize());
		}
		ShareQuantity.AmountSplit split = ShareQuantity.splitByAmount(request.getAmount(), price);
		return new ShareQuantity(split.shares());
	}

	/**
	 * Devuelve el valor que se persiste en la columna {@code size} cuando la
	 * operacion no llega a resolverse.
	 *
	 * <p>Si el cliente opero por importe y no hay cotizacion, el importe es lo
	 * unico valor conocido; si opero por cantidad, se conserva esa cantidad.</p>
	 *
	 * @param request solicitud de creacion
	 * @return el valor a persistir
	 */
	private BigDecimal requestedSize(CreateOrderRequest request) {
		if (request.getSize() != null) {
			return request.getSize();
		}
		if (request.getAmount() != null) {
			return request.getAmount();
		}
		throw new InvalidRequestException("Either 'size' or 'amount' must be provided");
	}

}
