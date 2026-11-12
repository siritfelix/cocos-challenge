package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code orders}.
 *
 * <p>Es el ledger central del sistema: los saldos y las posiciones se derivan
 * de estas filas (spec D-047).</p>
 *
 * <p>La columna {@code size} tiene semantica dual segun el lado de la
 * operacion: cantidad de acciones en compras y ventas, e importe en pesos en
 * las transferencias (spec D-045).</p>
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "instrument_id", nullable = false)
	private Long instrumentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "side", nullable = false, length = 20)
	private OrderSide side;

	@Column(name = "size", nullable = false, precision = 10, scale = 2)
	private BigDecimal size;

	@Column(name = "price", precision = 10, scale = 2)
	private BigDecimal price;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private OrderType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "datetime", nullable = false)
	private LocalDateTime datetime;

	protected OrderEntity() {
		// Constructor requerido por JPA.
	}

	/**
	 * @return el identificador de la orden
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return el identificador de la cuenta propietaria
	 */
	public Long getUserId() {
		return userId;
	}

	/**
	 * @return el identificador del instrumento negociado
	 */
	public Long getInstrumentId() {
		return instrumentId;
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
	 * @return el precio de ejecucion o limite
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
	 * @return el motivo del rechazo
	 */
	public String getRejectionReason() {
		return rejectionReason;
	}

	/**
	 * @return el instante de creacion
	 */
	public LocalDateTime getDatetime() {
		return datetime;
	}

	/**
	 * @param userId identificador de la cuenta propietaria
	 */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	/**
	 * @param instrumentId identificador del instrumento negociado
	 */
	public void setInstrumentId(Long instrumentId) {
		this.instrumentId = instrumentId;
	}

	/**
	 * @param side lado de la operacion
	 */
	public void setSide(OrderSide side) {
		this.side = side;
	}

	/**
	 * @param size cantidad de acciones o importe de la transferencia
	 */
	public void setSize(BigDecimal size) {
		this.size = size;
	}

	/**
	 * @param price precio de ejecucion o limite
	 */
	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	/**
	 * @param type tipo de ejecucion
	 */
	public void setType(OrderType type) {
		this.type = type;
	}

	/**
	 * @param status estado de la orden
	 */
	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	/**
	 * @param rejectionReason motivo del rechazo
	 */
	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	/**
	 * @param datetime instante de creacion
	 */
	public void setDatetime(LocalDateTime datetime) {
		this.datetime = datetime;
	}

}
