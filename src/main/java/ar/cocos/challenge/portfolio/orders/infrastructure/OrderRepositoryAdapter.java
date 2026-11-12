package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.orders.domain.RejectionReason;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link OrderRepository} sobre Spring Data JPA.
 *
 * <p>Traduce entre la entidad de infraestructura y el objeto de dominio, de
 * modo que la capa de dominio no conoce JPA (Principio V).</p>
 */
@Repository
public class OrderRepositoryAdapter implements OrderRepository {

	private static final List<OrderSide> TRADE_SIDES = List.of(OrderSide.BUY, OrderSide.SELL);

	private final JpaOrderRepository jpaOrderRepository;
	private final AccountRepository accountRepository;
	private final InstrumentRepository instrumentRepository;

	/**
	 * @param jpaOrderRepository repositorio Spring Data de ordenes
	 * @param accountRepository repositorio de cuentas
	 * @param instrumentRepository repositorio de instrumentos
	 */
	public OrderRepositoryAdapter(JpaOrderRepository jpaOrderRepository, AccountRepository accountRepository,
			InstrumentRepository instrumentRepository) {
		this.jpaOrderRepository = jpaOrderRepository;
		this.accountRepository = accountRepository;
		this.instrumentRepository = instrumentRepository;
	}

	@Override
	@Transactional
	public Order save(Order order) {
		OrderEntity saved = jpaOrderRepository.save(toEntity(order));
		return order.withId(saved.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Order> findById(Long orderId) {
		return jpaOrderRepository.findById(orderId).map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Order> findByIdAndAccountId(Long orderId, Long accountId) {
		return jpaOrderRepository.findById(orderId)
				.filter(entity -> entity.getUserId().equals(accountId))
				.map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal sumFilledCash(Long accountId) {
		return jpaOrderRepository.sumFilledCash(accountId);
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal sumReservedCash(Long accountId) {
		return jpaOrderRepository.sumReservedCash(accountId);
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal sumFilledShares(Long accountId, Long instrumentId) {
		return jpaOrderRepository.sumFilledShares(accountId, instrumentId);
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal sumReservedShares(Long accountId, Long instrumentId) {
		return jpaOrderRepository.sumReservedShares(accountId, instrumentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Long> findInstrumentsWithPosition(Long accountId) {
		return jpaOrderRepository.findInstrumentsWithPosition(accountId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Order> findExecutedTradesInChronologicalOrder(Long accountId, Long instrumentId) {
		List<OrderEntity> entities = jpaOrderRepository.findExecutedTradesInChronologicalOrder(accountId,
				instrumentId, TRADE_SIDES);
		if (entities.isEmpty()) {
			return List.of();
		}
		// La cuenta y el instrumento son los mismos para todas las ordenes de la
		// lista: se resuelven una sola vez para evitar una consulta por orden.
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new IllegalStateException("Account not found for orders"));
		Instrument instrument = instrumentRepository.findById(instrumentId)
				.orElseThrow(() -> new IllegalStateException("Instrument not found for orders"));
		return entities.stream().map(entity -> toDomain(entity, account, instrument)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Order> findHistory(Long accountId, OrderStatus status, OrderSide side, String ticker,
			Pageable pageable) {
		Page<OrderEntity> page = jpaOrderRepository.findHistory(accountId, status, side, ticker, pageable);
		// La cuenta y el instrumento se resuelven una vez por pagina: todas las
		// ordenes de la pagina pertenecen a la misma cuenta.
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new IllegalStateException("Account not found for orders"));
		Map<Long, Instrument> instruments = resolveInstruments(page.getContent());
		return page.map(entity -> toDomain(entity, account, instruments));
	}

	private Map<Long, Instrument> resolveInstruments(List<OrderEntity> entities) {
		List<Long> instrumentIds = entities.stream()
				.map(OrderEntity::getInstrumentId)
				.distinct()
				.toList();
		return instrumentIds.stream()
				.map(instrumentRepository::findById)
				.flatMap(Optional::stream)
				.collect(Collectors.toMap(Instrument::id, instrument -> instrument));
	}

	private Order toDomain(OrderEntity entity, Account account, Map<Long, Instrument> instruments) {
		Instrument instrument = instruments.get(entity.getInstrumentId());
		if (instrument == null) {
			throw new IllegalStateException("Instrument not found for order");
		}
		return toDomain(entity, account, instrument);
	}

	@Override
	@Transactional
	public Order update(Order order) {
		// Se actualiza solo el estado sobre la fila existente: el resto de los
		// campos de una orden son inmutables una vez creada.
		jpaOrderRepository.updateStatus(order.getId(), order.getStatus());
		return order;
	}

	private OrderEntity toEntity(Order order) {
		OrderEntity entity = new OrderEntity();
		entity.setUserId(order.getAccount().id());
		entity.setInstrumentId(order.getInstrument().id());
		entity.setSide(order.getSide());
		entity.setSize(order.getSize());
		entity.setPrice(order.getPrice());
		entity.setType(order.getType());
		entity.setStatus(order.getStatus());
		entity.setRejectionReason(
				order.getRejectionReason() == null ? null : order.getRejectionReason().name());
		entity.setDatetime(order.getDatetime());
		return entity;
	}

	private Order toDomain(OrderEntity entity) {
		Account account = accountRepository.findById(entity.getUserId())
				.orElseThrow(() -> new IllegalStateException("Account not found for order"));
		Instrument instrument = instrumentRepository.findById(entity.getInstrumentId())
				.orElseThrow(() -> new IllegalStateException("Instrument not found for order"));
		return toDomain(entity, account, instrument);
	}

	private Order toDomain(OrderEntity entity, Account account, Instrument instrument) {
		return Order.reconstituted(entity.getId(), account, instrument, entity.getSide(), entity.getSize(),
				entity.getPrice(), entity.getType(), entity.getStatus(), toRejectionReason(entity.getRejectionReason()),
				entity.getDatetime());
	}

	private RejectionReason toRejectionReason(String storedReason) {
		if (storedReason == null) {
			return null;
		}
		for (RejectionReason reason : RejectionReason.values()) {
			if (reason.name().equals(storedReason)) {
				return reason;
			}
		}
		return null;
	}

}
