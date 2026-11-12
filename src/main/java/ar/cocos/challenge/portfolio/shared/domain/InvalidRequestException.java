package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Error de dominio que se traduce a {@code 400 Bad Request}.
 *
 * <p>Se usa para las solicitudes invalidas detectadas por las reglas de
 * negocio que no encajan en la validacion declarativa del request (Principio
 * II).</p>
 */
public class InvalidRequestException extends DomainException {

	/**
	 * @param message descripcion legible del problema detectado
	 */
	public InvalidRequestException(String message) {
		super(message);
	}

}
