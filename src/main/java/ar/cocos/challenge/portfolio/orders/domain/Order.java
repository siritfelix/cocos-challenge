package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.users.domain.Account;

/**
 * Orden registrada por una cuenta.
 *
 * <p>Es la fuente de verdad del efectivo y de las posiciones: los saldos no se
 * persisten, se derivan de las ordenes (spec D-047).</p>
 *
 * <p>La columna {@code size} tiene una semantica dual segun el lado de la
 * operacion: cantidad de acciones en {@code BUY} y {@code SELL}, e importe en
 * pesos en {@code CASH_IN} y {@code CASH_OUT} (spec D-045). El dominio mantiene
 * esa distincion mediante {@link ShareQuantity} y {@link CashAmount}.</p>
 */
public class Order {

	private final Long id;
	private final Account account;
	private final Instrument instrument;
	private final OrderSide side;
	private final BigDecimal size;
	private final BigDecimal price;
	private final OrderType type;
	private final OrderStatus status;
	private final RejectionReason rejectionReason;
	private final LocalDateTime datetime;

	private Order(Long id, Account account, Instrument instrument, OrderSide side, BigDecimal size, BigDecimal price,
			OrderType type, OrderStatus status, RejectionReason rejectionReason, LocalDateTime datetime) {
		this.id = id;
		this.account = account;
		this.instrument = instrument;
		this.side = side;
		this.size = size;
		this.price = price;
		this.type = type;
		this.status = status;
		this.rejectionReason = rejectionReason;
		this.datetime = datetime;
	}

	/**
	 * Crea una orden a mercado sobre acciones.
	 *
	 * @param account cuenta propietaria
	 * @param instrument instrumento negociado
	 * @param side lado de la operacion
	 * @param quantity cantidad de acciones
	 * @param price precio de ejecucion
	 * @param datetime instante de creacion
	 * @return la orden en estado {@code FILLED}
	 */
	public static Order marketTrade(Account account, Instrument instrument, OrderSide side, ShareQuantity quantity,
			BigDecimal price, LocalDateTime datetime) {
		Objects.requireNonNull(price, "price must not be null");
		return new Order(null, account, instrument, side, quantity.value(), price, OrderType.MARKET, OrderStatus.FILLED,
				null, datetime);
	}

	/**
	 * Crea una orden limite sobre acciones, que queda pendiente y reserva
	 * recursos.
	 *
	 * @param account cuenta propietaria
	 * @param instrument instrumento negociado
	 * @param side lado de la operacion
	 * @param quantity cantidad de acciones
	 * @param limitPrice precio limite
	 * @param datetime instante de creacion
	 * @return la orden en estado {@code NEW}
	 */
	public static Order limitTrade(Account account, Instrument instrument, OrderSide side, ShareQuantity quantity,
			BigDecimal limitPrice, LocalDateTime datetime) {
		Objects.requireNonNull(limitPrice, "limitPrice must not be null");
		return new Order(null, account, instrument, side, quantity.value(), limitPrice, OrderType.LIMIT, OrderStatus.NEW,
				null, datetime);
	}

	/**
	 * Crea una transferencia de efectivo.
	 *
	 * @param account cuenta propietaria
	 * @param instrument instrumento de efectivo
	 * @param side lado de la operacion
	 * @param amount importe en pesos
	 * @param datetime instante de creacion
	 * @return la orden en estado {@code FILLED}
	 */
	public static Order transfer(Account account, Instrument instrument, OrderSide side, CashAmount amount,
			LocalDateTime datetime) {
		return new Order(null, account, instrument, side, amount.value(), null, OrderType.MARKET, OrderStatus.FILLED,
				null, datetime);
	}

	/**
	 * Crea una orden rechazada por una regla de negocio.
	 *
	 * <p>El rechazo se persiste para dejar traza del intento (spec FR-042).</p>
	 *
	 * @param account cuenta propietaria
	 * @param instrument instrumento negociado
	 * @param side lado de la operacion
	 * @param size cantidad de acciones o importe de la transferencia
	 * @param price precio de ejecucion, o {@code null} si no llego a resolverse
	 * @param type tipo de la orden
	 * @param reason motivo del rechazo
	 * @param datetime instante de creacion
	 * @return la orden en estado {@code REJECTED}
	 */
	public static Order rejected(Account account, Instrument instrument, OrderSide side, BigDecimal size,
			BigDecimal price, OrderType type, RejectionReason reason, LocalDateTime datetime) {
		return new Order(null, account, instrument, side, size, price, type, OrderStatus.REJECTED, reason, datetime);
	}

	/**
	 * Reconstruye una orden a partir de su estado persistido.
	 *
	 * @param id identificador asignado por la persistencia
	 * @param account cuenta propietaria
	 * @param instrument instrumento negociado
	 * @param side lado de la operacion
	 * @param size cantidad de acciones o importe de la transferencia
	 * @param price precio de ejecucion o limite
	 * @param type tipo de ejecucion
	 * @param status estado de la orden
	 * @param rejectionReason motivo del rechazo, o {@code null}
	 * @param datetime instante de creacion
	 * @return la orden reconstruida
	 */
	public static Order reconstituted(Long id, Account account, Instrument instrument, OrderSide side,
			BigDecimal size, BigDecimal price, OrderType type, OrderStatus status, RejectionReason rejectionReason,
			LocalDateTime datetime) {
		return new Order(id, account, instrument, side, size, price, type, status, rejectionReason, datetime);
	}

	/**
	 * @return el identificador de la orden
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return la cuenta propietaria
	 */
	public Account getAccount() {
		return account;
	}

	/**
	 * @return el instrumento negociado
	 */
	public Instrument getInstrument() {
		return instrument;
	}

	/**
	 * @return el lado de la operacion
	 */
	public OrderSide getSide() {
		return side;
	}

	/**
	 * @return la cantidad de acciones o el importe de la transferencia
	 */
	public BigDecimal getSize() {
		return size;
	}

	/**
	 * @return el precio de ejecucion o limite; {@code null} en las transferencias
	 */
	public BigDecimal getPrice() {
		return price;
	}

	/**
	 * @return el tipo de ejecucion
	 */
	public OrderType getType() {
		return type;
	}

	/**
	 * @return el estado de la orden
	 */
	public OrderStatus getStatus() {
		return status;
	}

	/**
	 * @return el motivo del rechazo, o {@code null} si la orden no fue rechazada
	 */
	public RejectionReason getRejectionReason() {
		return rejectionReason;
	}

	/**
	 * @return el instante de creacion en UTC
	 */
	public LocalDateTime getDatetime() {
		return datetime;
	}

	/**
	 * @return el costo total de la operacion: cantidad por precio
	 */
	public BigDecimal getGrossAmount() {
		if (price == null) {
			return BigDecimal.ZERO;
		}
		return size.multiply(price);
	}

	/**
	 * Devuelve una copia de la orden con identificador asignado.
	 *
	 * @param orderId identificador asignado por la persistencia
	 * @return la orden persistida
	 */
	public Order withId(Long orderId) {
		return new Order(orderId, account, instrument, side, size, price, type, status, rejectionReason, datetime);
	}

}
