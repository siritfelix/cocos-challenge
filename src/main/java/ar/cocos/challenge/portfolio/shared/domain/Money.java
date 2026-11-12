package ar.cocos.challenge.portfolio.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetario inmutable expresado en pesos argentinos (ARS).
 *
 * <p>Envuelve un {@link BigDecimal} para evitar el uso de tipos de punto
 * flotante en los calculos monetarios (spec FR-096) y para centralizar la
 * escala y el redondeo. Todos los importes de la aplicacion se representan con
 * dos decimales.</p>
 *
 * <p>Los comparaciones usan {@link BigDecimal#compareTo(BigDecimal)} y no
 * {@link BigDecimal#equals(Object)}, porque este ultimo distingue entre
 * {@code 10.00} y {@code 10} al comparar tambien la escala.</p>
 */
public final class Money implements Comparable<Money> {

	/**
	 * Escala de los importes monetarios.
	 */
	public static final int SCALE = 2;

	/**
	 * Redondeo aplicado a las operaciones monetarias.
	 */
	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private final BigDecimal amount;

	private Money(BigDecimal amount) {
		this.amount = amount;
	}

	/**
	 * Crea un importe a partir de su valor numerico.
	 *
	 * @param amount valor del importe; no puede ser nulo
	 * @return el importe monetario con la escala estandar
	 */
	public static Money of(BigDecimal amount) {
		Objects.requireNonNull(amount, "amount must not be null");
		return new Money(amount.setScale(SCALE, ROUNDING));
	}

	/**
	 * Crea un importe a partir de su representacion textual.
	 *
	 * @param amount texto del importe; no puede ser nulo
	 * @return el importe monetario con la escala estandar
	 */
	public static Money of(String amount) {
		return of(new BigDecimal(amount));
	}

	/**
	 * Devuelve el importe cero.
	 *
	 * @return el importe 0.00
	 */
	public static Money zero() {
		return new Money(BigDecimal.ZERO.setScale(SCALE, ROUNDING));
	}

	/**
	 * @return el valor del importe con dos decimales
	 */
	public BigDecimal amount() {
		return amount;
	}

	/**
	 * Suma otro importe a este.
	 *
	 * @param other importe a sumar; no puede ser nulo
	 * @return el importe resultante
	 */
	public Money plus(Money other) {
		return new Money(amount.add(other.amount).setScale(SCALE, ROUNDING));
	}

	/**
	 * Resta otro importe a este.
	 *
	 * @param other importe a restar; no puede ser nulo
	 * @return el importe resultante
	 */
	public Money minus(Money other) {
		return new Money(amount.subtract(other.amount).setScale(SCALE, ROUNDING));
	}

	/**
	 * Multiplica este importe por un factor, redondeando a la escala estandar.
	 *
	 * <p>Se usa para calcular el costo de una operacion
	 * (cantidad por precio) sin perder precision decimal.</p>
	 *
	 * @param factor factor de multiplicacion; no puede ser nulo
	 * @return el importe resultante
	 */
	public Money multipliedBy(BigDecimal factor) {
		return new Money(amount.multiply(factor).setScale(SCALE, ROUNDING));
	}

	/**
	 * @return {@code true} si el importe es mayor que cero
	 */
	public boolean isPositive() {
		return amount.signum() > 0;
	}

	/**
	 * @return {@code true} si el importe es cero
	 */
	public boolean isZero() {
		return amount.signum() == 0;
	}

	/**
	 * @return {@code true} si el importe es menor que cero
	 */
	public boolean isNegative() {
		return amount.signum() < 0;
	}

	/**
	 * Compara este importe con otro por su valor numerico, ignorando la escala.
	 *
	 * @param other importe a comparar; no puede ser nulo
	 * @return un entero negativo, cero o positivo segun el orden
	 */
	@Override
	public int compareTo(Money other) {
		return amount.compareTo(other.amount);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Money money)) {
			return false;
		}
		return amount.compareTo(money.amount) == 0;
	}

	@Override
	public int hashCode() {
		return amount.stripTrailingZeros().hashCode();
	}

	@Override
	public String toString() {
		return amount.toPlainString();
	}

}
