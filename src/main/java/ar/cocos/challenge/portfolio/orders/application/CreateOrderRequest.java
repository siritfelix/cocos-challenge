package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Solicitud de creacion de una orden o de una transferencia.
 *
 * <p>El request debe contener exactamente uno de los campos {@code size} o
 * {@code amount} (spec FR-016, D-011). Para las compras y las ventas ambos son
 * admitidos; para las transferencias solo {@code size} (spec FR-020).</p>
 *
 * <p>Las reglas que dependen del lado de la operacion (por ejemplo, que una
 * orden a mercado no admita precio) se validan en el dominio, no aqui, porque
 * requieren contexto de negocio y no solo la forma del request.</p>
 */
public class CreateOrderRequest {

	@NotBlank
	private String ticker;

	@NotNull
	private OrderSide side;

	@NotNull
	private OrderType type;

	@Positive
	private BigDecimal size;

	@Positive
	private BigDecimal amount;

	@Positive
	private BigDecimal price;

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
	 * @return el importe a asignar a la operacion
	 */
	public BigDecimal getAmount() {
		return amount;
	}

	/**
	 * @param amount importe a asignar a la operacion
	 */
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	/**
	 * @return el precio limite
	 */
	public BigDecimal getPrice() {
		return price;
	}

	/**
	 * @param price precio limite
	 */
	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}
