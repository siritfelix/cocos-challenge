package ar.cocos.challenge.portfolio.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyKey;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyRepository;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import ar.cocos.challenge.portfolio.orders.domain.RejectionReason;
import ar.cocos.challenge.portfolio.orders.domain.ResourceCalculator;
import ar.cocos.challenge.portfolio.shared.application.ClockProvider;
import ar.cocos.challenge.portfolio.shared.domain.ConflictException;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests unitarios del caso de uso de creacion de ordenes.
 *
 * <p>Verifican las reglas de negocio aisladas de la infraestructura: rechazo
 * por saldo insuficiente, por acciones insuficientes, por ausencia de
 * cotizacion y por importe insuficiente, ademas de la idempotencia.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateOrderUseCaseTest {

	private static final String ACCOUNT_NUMBER = "10001";
	private static final String KEY = "key-1";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 4, 30, 0);

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private InstrumentRepository instrumentRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private IdempotencyRepository idempotencyRepository;

	@Mock
	private ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository marketDataRepository;

	@Mock
	private ClockProvider clockProvider;

	private CreateOrderUseCase createOrderUseCase;
	private Account account;
	private Instrument stock;
	private Instrument cash;

	@BeforeEach
	void setUp() {
		account = new Account(1L, "emiliano@test.com", ACCOUNT_NUMBER);
		stock = new Instrument(10L, "YPFD", "Y.P.F. S.A.", ar.cocos.challenge.portfolio.instruments.domain.InstrumentType.ACCIONES);
		cash = new Instrument(11L, "ARS", "Peso argentino", ar.cocos.challenge.portfolio.instruments.domain.InstrumentType.MONEDA);

		ResourceCalculator resourceCalculator = new ResourceCalculator(orderRepository);
		OrderValidator orderValidator = new OrderValidator();
		OrderPricingPolicy pricingPolicy = new OrderPricingPolicy(marketDataRepository);

		createOrderUseCase = new CreateOrderUseCase(accountRepository, instrumentRepository, orderRepository,
				idempotencyRepository, resourceCalculator, orderValidator, pricingPolicy, new RequestHasher(),
				clockProvider);

		given(clockProvider.now()).willReturn(NOW);
		given(accountRepository.findByAccountNumberForUpdate(ACCOUNT_NUMBER)).willReturn(Optional.of(account));
		given(idempotencyRepository.findByAccountIdAndKey(any(), any())).willReturn(Optional.empty());
		given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
			Order order = invocation.getArgument(0);
			return order.withId(100L);
		});
	}

	@Test
	@DisplayName("Una compra a mercado sin efectivo suficiente se persiste como REJECTED")
	void rejectsBuyWithoutCash() {
		given(instrumentRepository.findByTicker("YPFD")).willReturn(Optional.of(stock));
		given(marketDataRepository.findLatestByInstrumentId(10L)).willReturn(Optional.of(quote("1000.00")));
		given(orderRepository.sumFilledCash(1L)).willReturn(new BigDecimal("100.00"));
		given(orderRepository.sumReservedCash(1L)).willReturn(BigDecimal.ZERO);

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("YPFD", OrderSide.BUY, OrderType.MARKET,
				"10", null, null));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
	}

	@Test
	@DisplayName("Una venta a mercado sin acciones suficientes se persiste como REJECTED")
	void rejectsSellWithoutShares() {
		given(instrumentRepository.findByTicker("YPFD")).willReturn(Optional.of(stock));
		given(marketDataRepository.findLatestByInstrumentId(10L)).willReturn(Optional.of(quote("1000.00")));
		given(orderRepository.sumFilledShares(1L, 10L)).willReturn(new BigDecimal("5.00"));
		given(orderRepository.sumReservedShares(1L, 10L)).willReturn(BigDecimal.ZERO);

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("YPFD", OrderSide.SELL, OrderType.MARKET,
				"10", null, null));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
	}

	@Test
	@DisplayName("Una compra a mercado sin cotizacion se persiste como REJECTED")
	void rejectsMarketOrderWithoutMarketData() {
		given(instrumentRepository.findByTicker("IRCP")).willReturn(Optional.of(
				new Instrument(12L, "IRCP", "IRSA Propiedades", ar.cocos.challenge.portfolio.instruments.domain.InstrumentType.ACCIONES)));

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("IRCP", OrderSide.BUY, OrderType.MARKET,
				"10", null, null));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.NO_MARKET_DATA);
	}

	@Test
	@DisplayName("Una compra por importe insuficiente para una accion se persiste como REJECTED")
	void rejectsBuyWithAmountTooSmall() {
		given(instrumentRepository.findByTicker("YPFD")).willReturn(Optional.of(stock));
		// Cotizacion de 1000: el importe de 500 no alcanza para una accion.
		given(marketDataRepository.findLatestByInstrumentId(10L)).willReturn(Optional.of(
				new ar.cocos.challenge.portfolio.instruments.domain.LatestQuote(new BigDecimal("1000.00"),
						new BigDecimal("990.00"), NOW)));
		given(orderRepository.sumFilledCash(1L)).willReturn(new BigDecimal("100000.00"));
		given(orderRepository.sumReservedCash(1L)).willReturn(BigDecimal.ZERO);

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("YPFD", OrderSide.BUY, OrderType.MARKET,
				null, "500", null));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.AMOUNT_TOO_SMALL);
	}

	@Test
	@DisplayName("Una compra por importe calcula la cantidad maxima de acciones")
	void createsBuyByAmount() {
		given(instrumentRepository.findByTicker("YPFD")).willReturn(Optional.of(stock));
		// Cotizacion de 1000: 5000 permiten 5 acciones y sobran 0.
		given(marketDataRepository.findLatestByInstrumentId(10L)).willReturn(Optional.of(
				new ar.cocos.challenge.portfolio.instruments.domain.LatestQuote(new BigDecimal("1000.00"),
						new BigDecimal("990.00"), NOW)));
		given(orderRepository.sumFilledCash(1L)).willReturn(new BigDecimal("100000.00"));
		given(orderRepository.sumReservedCash(1L)).willReturn(BigDecimal.ZERO);

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("YPFD", OrderSide.BUY, OrderType.MARKET,
				null, "5000", null));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		assertThat(order.getSize()).isEqualByComparingTo("5");
	}

	@Test
	@DisplayName("Una compra limite con recursos suficientes queda NEW")
	void createsPendingLimitOrder() {
		given(instrumentRepository.findByTicker("YPFD")).willReturn(Optional.of(stock));
		given(orderRepository.sumFilledCash(1L)).willReturn(new BigDecimal("100000.00"));
		given(orderRepository.sumReservedCash(1L)).willReturn(BigDecimal.ZERO);

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY, request("YPFD", OrderSide.BUY, OrderType.LIMIT,
				"1", null, "20000.00"));

		assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
		assertThat(order.getPrice()).isEqualByComparingTo("20000.00");
	}

	@Test
	@DisplayName("Una clave repetida con contenido distinto responde conflicto")
	void rejectsSameKeyWithDifferentContent() {
		given(idempotencyRepository.findByAccountIdAndKey(1L, KEY))
				.willReturn(Optional.of(new IdempotencyKey(1L, 1L, KEY, "otra-huella", 100L, NOW)));

		assertThatThrownBy(() -> createOrderUseCase.create(ACCOUNT_NUMBER, KEY,
				request("YPFD", OrderSide.BUY, OrderType.LIMIT, "1", null, "20000.00")))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("Una clave repetida con el mismo contenido devuelve la orden original")
	void replaysOriginalOrder() {
		String hash = new RequestHasher()
				.hash(request("YPFD", OrderSide.BUY, OrderType.LIMIT, "1", null, "20000.00"));
		given(idempotencyRepository.findByAccountIdAndKey(1L, KEY))
				.willReturn(Optional.of(new IdempotencyKey(1L, 1L, KEY, hash, 100L, NOW)));
		given(orderRepository.findById(100L)).willReturn(Optional.of(Order.reconstituted(100L, account, stock,
				OrderSide.BUY, new BigDecimal("1"), new BigDecimal("20000.00"), OrderType.LIMIT, OrderStatus.NEW, null,
				NOW)));

		Order order = createOrderUseCase.create(ACCOUNT_NUMBER, KEY,
				request("YPFD", OrderSide.BUY, OrderType.LIMIT, "1", null, "20000.00"));

		assertThat(order.getId()).isEqualTo(100L);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
		verify(orderRepository, never()).save(any());
	}

	@Test
	@DisplayName("Una solicitud invalida no se persiste")
	void doesNotPersistInvalidRequest() {
		assertThatThrownBy(() -> createOrderUseCase.create(ACCOUNT_NUMBER, KEY,
				request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", "1000", null)))
				.isInstanceOf(InvalidRequestException.class);

		verify(orderRepository, never()).save(any());
		verify(idempotencyRepository, never()).save(any());
	}

	private CreateOrderRequest request(String ticker, OrderSide side, OrderType type, String size, String amount,
			String price) {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setTicker(ticker);
		request.setSide(side);
		request.setType(type);
		if (size != null) {
			request.setSize(new BigDecimal(size));
		}
		if (amount != null) {
			request.setAmount(new BigDecimal(amount));
		}
		if (price != null) {
			request.setPrice(new BigDecimal(price));
		}
		return request;
	}

	private ar.cocos.challenge.portfolio.instruments.domain.LatestQuote quote(String close) {
		return new ar.cocos.challenge.portfolio.instruments.domain.LatestQuote(new BigDecimal(close),
				new BigDecimal("990.00"), NOW);
	}

}
