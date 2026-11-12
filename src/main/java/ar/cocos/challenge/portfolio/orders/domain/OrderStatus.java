package ar.cocos.challenge.portfolio.orders.domain;

/**
 * Estado de una orden.
 */
public enum OrderStatus {

	/**
	 * Orden limite pendiente. Mantiene recursos reservados.
	 */
	NEW,

	/**
	 * Orden ejecutada.
	 */
	FILLED,

	/**
	 * Orden rechazada por una regla de negocio. Es terminal.
	 */
	REJECTED,

	/**
	 * Orden cancelada desde {@code NEW}. Es terminal.
	 */
	CANCELLED;

	/**
	 * @return {@code true} si el estado es terminal y la orden ya no admite
	 *         cambios de estado
	 */
	public boolean isTerminal() {
		return this == FILLED || this == REJECTED || this == CANCELLED;
	}

}
