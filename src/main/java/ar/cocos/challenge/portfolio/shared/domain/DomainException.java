package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Raiz de los errores del dominio.
 *
 * <p>Las excepciones de dominio describen una violacion de una regla de
 * negocio y se traducen a una respuesta HTTP por el manejador global de
 * errores. No dependen de Spring ni de la infraestructura, para que la capa de
 * dominio permanezca independiente de los frameworks (Principio V).</p>
 */
public abstract class DomainException extends RuntimeException {

	/**
	 * @param message descripcion legible de la regla incumplida
	 */
	protected DomainException(String message) {
		super(message);
	}

}
