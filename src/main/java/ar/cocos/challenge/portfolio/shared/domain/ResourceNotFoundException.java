package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Error de dominio que se traduce a {@code 404 Not Found}.
 *
 * <p>Indica que el recurso solicitado no existe, por ejemplo un instrumento
 * con un ticker desconocido (spec FR-089).</p>
 */
public class ResourceNotFoundException extends DomainException {

	/**
	 * @param message descripcion legible del recurso ausente
	 */
	public ResourceNotFoundException(String message) {
		super(message);
	}

}
