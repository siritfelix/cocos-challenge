package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Error de dominio que se traduce a {@code 409 Conflict}.
 *
 * <p>Indica que la operacion no puede realizarse por el estado actual del
 * recurso, por ejemplo al cancelar una orden que ya no esta en estado
 * {@code NEW} (spec FR-064).</p>
 */
public class ConflictException extends DomainException {

	/**
	 * @param message descripcion legible del conflicto detectado
	 */
	public ConflictException(String message) {
		super(message);
	}

}
