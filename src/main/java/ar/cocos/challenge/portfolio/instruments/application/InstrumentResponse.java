package ar.cocos.challenge.portfolio.instruments.application;

import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;

/**
 * Instrumento del catalogo en la respuesta de la API (spec FR-006).
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo.</p>
 */
public class InstrumentResponse {

	private Long id;
	private String ticker;
	private String name;
	private InstrumentType type;

	/**
	 * @return el identificador interno del instrumento
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @param id identificador interno del instrumento
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * @return el simbolo del instrumento
	 */
	public String getTicker() {
		return ticker;
	}

	/**
	 * @param ticker simbolo del instrumento
	 */
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	/**
	 * @return el nombre descriptivo del instrumento
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name nombre descriptivo del instrumento
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return el tipo del instrumento
	 */
	public InstrumentType getType() {
		return type;
	}

	/**
	 * @param type tipo del instrumento
	 */
	public void setType(InstrumentType type) {
		this.type = type;
	}

}
