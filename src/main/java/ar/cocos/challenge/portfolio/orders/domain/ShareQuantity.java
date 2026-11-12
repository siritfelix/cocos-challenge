package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cantidad de acciones de una operacion.
 *
 * <p>Las cantidades de acciones son siempre enteras (spec FR-017): no se
 * admiten fracciones de acciones. Se admiten en cero unicamente como resultado
 * del reparto de un importe insuficiente, caso en el que la operacion se
 * rechaza (spec FR-032).</p>
 */
public record ShareQuantity(BigDecimal value) implements Comparable<ShareQuantity> {

	/**
	 * @param value cantidad de acciones; debe ser entera y no negativa
	 */
	public ShareQuantity {
		Objects.requireNonNull(value, "value must not be null");
		if (value.signum() < 0) {
			throw new IllegalArgumentException("Share quantity must not be negative");
		}
		if (value.stripTrailingZeros().scale() > 0) {
			throw new IllegalArgumentException("Share quantity must be a whole number");
		}
		value = value.setScale(0, RoundingMode.UNNECESSARY);
	}

	/**
	 * Crea una cantidad de acciones a partir de un valor entero positivo.
	 *
	 * @param value cantidad de acciones
	 * @return la cantidad de acciones
	 */
	public static ShareQuantity of(BigDecimal value) {
		if (value == null || value.signum() <= 0) {
			throw new IllegalArgumentException("Share quantity must be greater than zero");
		}
		return new ShareQuantity(value);
	}

	/**
	 * @return {@code true} si la cantidad es cero
	 */
	public boolean isZero() {
		return value.signum() == 0;
	}

	/**
	 * Calcula la cantidad maxima de acciones enteras cuyo valor no supera un
	 * importe, y devuelve el sobrante.
	 *
	 * <p>Es la regla de la spec FR-030: al operar por importe, el sobrante
	 * permanece disponible en la cuenta. Si el importe no alcanza para una
	 * sola accion, la cantidad resulta cero.</p>
	 *
	 * @param amount importe a asignar
	 * @param price precio de referencia de la accion
	 * @return la cantidad de acciones y el importe sobrante
	 */
	public static AmountSplit splitByAmount(BigDecimal amount, BigDecimal price) {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(price, "price must not be null");
		if (price.signum() <= 0) {
			throw new IllegalArgumentException("Price must be greater than zero");
		}
		BigDecimal shares = amount.divide(price, 0, RoundingMode.FLOOR);
		BigDecimal consumed = shares.multiply(price);
		BigDecimal remainder = amount.subtract(consumed);
		return new AmountSplit(shares, remainder);
	}

	/**
	 * @param other cantidad a comparar
	 * @return comparacion numerica de las cantidades
	 */
	@Override
	public int compareTo(ShareQuantity other) {
		return value.compareTo(other.value);
	}

	/**
	 * Resultado de repartir un importe entre acciones.
	 *
	 * @param shares cantidad de acciones obtenidas
	 * @param remainder importe sobrante, que permanece disponible
	 */
	public record AmountSplit(BigDecimal shares, BigDecimal remainder) {

		/**
		 * @return la cantidad de acciones, que puede ser cero
		 */
		public BigDecimal shares() {
			return shares;
		}

		/**
		 * @return {@code true} si el importe no alcanza para una sola accion
		 */
		public boolean isEmpty() {
			return shares.signum() == 0;
		}

	}

}
