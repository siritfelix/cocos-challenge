package ar.cocos.challenge.portfolio.shared.infrastructure;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estructura uniforme de las respuestas de error de la API.
 *
 * <p>Cumple la forma definida por la spec (seccion 14): marca de tiempo, codigo
 * de estado, descripcion del error, mensaje y ruta. El campo {@code details}
 * es opcional y solo se completa en los errores de validacion, donde detalla
 * los campos invalidos.</p>
 *
 * @param timestamp instante del error en UTC, ISO-8601 sin zona horaria
 * @param status codigo de estado HTTP
 * @param error descripcion del error
 * @param message mensaje legible del error
 * @param path ruta de la solicitud que fallo
 * @param details detalle por campo de los errores de validacion; vacio en el resto
 */
public record ErrorResponse(
		LocalDateTime timestamp,
		int status,
		String error,
		String message,
		String path,
		List<String> details) {

	/**
	 * Crea una respuesta de error sin detalle por campo.
	 *
	 * @param timestamp instante del error en UTC
	 * @param status codigo de estado HTTP
	 * @param error descripcion del error
	 * @param message mensaje legible del error
	 * @param path ruta de la solicitud que fallo
	 * @return la respuesta de error
	 */
	public static ErrorResponse of(LocalDateTime timestamp, int status, String error, String message,
			String path) {
		return new ErrorResponse(timestamp, status, error, message, path, List.of());
	}

}
