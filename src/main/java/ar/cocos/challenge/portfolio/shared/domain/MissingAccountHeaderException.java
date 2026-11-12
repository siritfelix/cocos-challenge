package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Error de dominio que se traduce a {@code 400 Bad Request}.
 *
 * <p>Indica que la solicitud no incluye la cabecera de identificacion de
 * cuenta {@code X-AccountNumber} (spec FR-002).</p>
 */
public class MissingAccountHeaderException extends DomainException {

	/**
	 * @param message descripcion legible del problema detectado
	 */
	public MissingAccountHeaderException(String message) {
		super(message);
	}

}
