package ar.cocos.challenge.portfolio.orders.domain;

import ar.cocos.challenge.portfolio.shared.domain.ConflictException;

/**
 * Error de dominio que se traduce a {@code 409 Conflict}.
 *
 * <p>Indica que la operacion no puede realizarse por el estado actual de la
 * orden: solo las ordenes en estado {@code NEW} pueden cancelarse (spec FR-062,
 * FR-064).</p>
 */
public class InvalidOrderStateException extends ConflictException {

	/**
	 * @param message descripcion legible del conflicto de estado
	 */
	public InvalidOrderStateException(String message) {
		super(message);
	}

	/**
	 * Indica que la orden no admite cancelacion por su estado actual.
	 *
	 * @param orderId identificador de la orden
	 * @param status estado actual de la orden
	 * @return la excepcion con el mensaje correspondiente
	 */
	public static InvalidOrderStateException notCancellable(Long orderId, OrderStatus status) {
		return new InvalidOrderStateException(
				"Order " + orderId + " cannot be cancelled because its status is " + status);
	}

}
