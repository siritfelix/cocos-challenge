package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import ar.cocos.challenge.portfolio.shared.domain.Money;

/**
 * Importe en pesos de una transferencia de efectivo.
 *
 * <p>Las transferencias se expresan siempre con el campo {@code size} de la
 * orden, que representa un importe en pesos (spec D-045).</p>
 */
public record CashAmount(BigDecimal value) implements Comparable<CashAmount> {

	/**
	 * @param value importe en pesos; debe ser mayor que cero
	 */
	public CashAmount {
		Objects.requireNonNull(value, "value must not be null");
		if (value.signum() <= 0) {
			throw new IllegalArgumentException("Cash amount must be greater than zero");
		}
		value = value.setScale(Money.SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * @param value importe en pesos
	 * @return el importe
	 */
	public static CashAmount of(BigDecimal value) {
		return new CashAmount(value);
	}

	/**
	 * @param other importe a comparar
	 * @return comparacion numerica de los importes
	 */
	@Override
	public int compareTo(CashAmount other) {
		return value.compareTo(other.value);
	}

	/**
	 * @return el importe como valor monetario
	 */
	public Money toMoney() {
		return Money.of(value);
	}

}
