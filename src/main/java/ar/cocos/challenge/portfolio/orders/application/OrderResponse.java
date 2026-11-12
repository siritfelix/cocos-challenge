package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;

/**
 * Respuesta de la API al crear o consultar una orden (spec seccion 10).
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo (verificado empiricamente).</p>
 */
public class OrderResponse {

	private Long id;
	private String ticker;
	private OrderSide side;
	private OrderType type;
	private BigDecimal size;
	private BigDecimal price;
	private OrderStatus status;
	private String rejectionReason;
	private LocalDateTime datetime;

	/**
	 * @return el identificador de la orden
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @param id identificador de la orden
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * @return el ticker del instrumento
	 */
	public String getTicker() {
		return ticker;
	}

	/**
	 * @param ticker ticker del instrumento
	 */
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	/**
	 * @return el lado de la operacion
	 */
	public OrderSide getSide() {
		return side;
	}

	/**
	 * @param side lado de la operacion
	 */
	public void setSide(OrderSide side) {
		this.side = side;
	}

	/**
	 * @return el tipo de ejecucion
	 */
	public OrderType getType() {
		return type;
	}

	/**
	 * @param type tipo de ejecucion
	 */
	public void setType(OrderType type) {
		this.type = type;
	}

	/**
	 * @return la cantidad de acciones o el importe de la transferencia
	 */
	public BigDecimal getSize() {
		return size;
	}

	/**
	 * @param size cantidad de acciones o importe de la transferencia
	 */
	public void setSize(BigDecimal size) {
		this.size = size;
	}

	/**
	 * @return el precio de ejecucion o limite
	 */
	public BigDecimal getPrice() {
		return price;
	}

	/**
	 * @param price precio de ejecucion o limite
	 */
	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	/**
	 * @return el estado de la orden
	 */
	public OrderStatus getStatus() {
		return status;
	}

	/**
	 * @param status estado de la orden
	 */
	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	/**
	 * @return el motivo del rechazo, o {@code null} si no fue rechazada
	 */
	public String getRejectionReason() {
		return rejectionReason;
	}

	/**
	 * @param rejectionReason motivo del rechazo
	 */
	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	/**
	 * @return el instante de creacion en UTC
	 */
	public LocalDateTime getDatetime() {
		return datetime;
	}

	/**
	 * @param datetime instante de creacion
	 */
	public void setDatetime(LocalDateTime datetime) {
		this.datetime = datetime;
	}

}
