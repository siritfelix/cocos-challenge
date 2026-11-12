package ar.cocos.challenge.portfolio.orders.domain;

/**
 * Lado de la operacion de una orden.
 *
 * <p>Las compras y ventas operan sobre acciones; las transferencias de efectivo
 * operan sobre el instrumento de efectivo {@code ARS} (spec FR-015, FR-021,
 * FR-022).</p>
 */
public enum OrderSide {

	/**
	 * Compra de acciones.
	 */
	BUY,

	/**
	 * Venta de acciones.
	 */
	SELL,

	/**
	 * Ingreso de efectivo.
	 */
	CASH_IN,

	/**
	 * Retiro de efectivo.
	 */
	CASH_OUT;

	/**
	 * @return {@code true} si el lado corresponde a una operacion sobre acciones
	 */
	public boolean isTrade() {
		return this == BUY || this == SELL;
	}

	/**
	 * @return {@code true} si el lado corresponde a una transferencia de efectivo
	 */
	public boolean isTransfer() {
		return this == CASH_IN || this == CASH_OUT;
	}

	/**
	 * @return {@code true} si el lado aumenta los recursos de la cuenta
	 */
	public boolean isCredit() {
		return this == SELL || this == CASH_IN;
	}

}
