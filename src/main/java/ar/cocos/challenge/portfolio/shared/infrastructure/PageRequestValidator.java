package ar.cocos.challenge.portfolio.shared.infrastructure;

import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * Valida los parametros de paginacion del historial de ordenes.
 *
 * <p>La pagina debe ser mayor o igual a cero y el tamano de pagina debe estar
 * entre 1 y 100 (spec FR-082). Los valores invalidos producen un error de
 * solicitud, nunca una exception no controlada.</p>
 */
@Component
public class PageRequestValidator {

	/**
	 * Tamano de pagina minimo admitido.
	 */
	public static final int MIN_PAGE_SIZE = 1;

	/**
	 * Tamano de pagina maximo admitido.
	 */
	public static final int MAX_PAGE_SIZE = 100;

	/**
	 * Valida los parametros de paginacion.
	 *
	 * @param page pagina solicitada, base 0
	 * @param size cantidad de elementos por pagina
	 * @throws InvalidRequestException si algun parametro esta fuera de rango
	 */
	public void validate(int page, int size) {
		if (page < 0) {
			throw new InvalidRequestException("Parameter 'page' must be greater than or equal to 0");
		}
		if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
			throw new InvalidRequestException(
					"Parameter 'size' must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
		}
	}

}
