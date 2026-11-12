package ar.cocos.challenge.portfolio.orders.application;

import java.util.List;

/**
 * Pagina del historial de ordenes en la respuesta de la API (spec FR-083).
 *
 * <p>Expone el contenido y los metadatos de paginacion: {@code content},
 * {@code totalElements}, {@code totalPages}, {@code page} y {@code size}.</p>
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo.</p>
 */
public class OrderPageResponse {

	private List<OrderResponse> content;
	private long totalElements;
	private int totalPages;
	private int page;
	private int size;

	/**
	 * @return las ordenes de la pagina
	 */
	public List<OrderResponse> getContent() {
		return content;
	}

	/**
	 * @param content ordenes de la pagina
	 */
	public void setContent(List<OrderResponse> content) {
		this.content = content;
	}

	/**
	 * @return la cantidad total de ordenes que satisfacen el filtro
	 */
	public long getTotalElements() {
		return totalElements;
	}

	/**
	 * @param totalElements cantidad total de ordenes
	 */
	public void setTotalElements(long totalElements) {
		this.totalElements = totalElements;
	}

	/**
	 * @return la cantidad total de paginas
	 */
	public int getTotalPages() {
		return totalPages;
	}

	/**
	 * @param totalPages cantidad total de paginas
	 */
	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	/**
	 * @return la pagina actual, base 0
	 */
	public int getPage() {
		return page;
	}

	/**
	 * @param page pagina actual, base 0
	 */
	public void setPage(int page) {
		this.page = page;
	}

	/**
	 * @return la cantidad de elementos por pagina
	 */
	public int getSize() {
		return size;
	}

	/**
	 * @param size cantidad de elementos por pagina
	 */
	public void setSize(int size) {
		this.size = size;
	}

}
