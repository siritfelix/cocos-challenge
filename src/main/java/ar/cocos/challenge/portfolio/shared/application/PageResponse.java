package ar.cocos.challenge.portfolio.shared.application;

import java.util.List;

/**
 * Pagina de resultados con los metadatos de paginacion del contrato HTTP.
 *
 * <p>El historial de ordenes devuelve esta estructura con los campos
 * {@code content}, {@code totalElements}, {@code totalPages}, {@code page} y
 * {@code size} (spec FR-083).</p>
 *
 * @param <T> tipo de los elementos de la pagina
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

	/**
	 * Crea una pagina a partir de su contenido y metadatos.
	 *
	 * @param content elementos de la pagina; no puede ser nulo
	 * @param totalElements cantidad total de elementos que satisfacen el filtro
	 * @param totalPages cantidad total de paginas
	 * @param page pagina actual, base 0
	 * @param size cantidad de elementos por pagina
	 */
	public PageResponse {
		content = List.copyOf(content);
	}

}
