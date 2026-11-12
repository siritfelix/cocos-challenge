package ar.cocos.challenge.portfolio.instruments.application;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Instrumento del catalogo en la respuesta de la API (spec FR-006).
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo.</p>
 *
 * <p>Los campos de precio son opcionales y la serializacion omite los nulos:
 * un instrumento sin cotizacion utilizable no los expone (spec 002,
 * FR-002 y FR-003).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstrumentResponse {

	private Long id;
	private String ticker;
	private String name;
	private InstrumentType type;

	/**
	 * Ultimo precio de mercado del instrumento: el cierre de su cotizacion mas
	 * reciente. Solo se informa cuando la cotizacion es utilizable segun
	 * {@code LatestQuote.isAvailable()}; de lo contrario queda en {@code null}
	 * y no aparece en el JSON.
	 */
	private BigDecimal price;

	/**
	 * Cierre anterior de la cotizacion mas reciente del instrumento. Solo se
	 * informa cuando esa cotizacion lo declara segun
	 * {@code LatestQuote.hasPreviousClose()}; de lo contrario queda en
	 * {@code null} y no aparece en el JSON.
	 */
	private BigDecimal previousClose;

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

	/**
	 * @return el ultimo precio de mercado, o {@code null} si no hay cotizacion
	 *         utilizable
	 */
	public BigDecimal getPrice() {
		return price;
	}

	/**
	 * @param price ultimo precio de mercado, o {@code null} si no hay
	 *            cotizacion utilizable
	 */
	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	/**
	 * @return el cierre anterior de la cotizacion mas reciente, o {@code null}
	 *         si esa cotizacion no lo declara
	 */
	public BigDecimal getPreviousClose() {
		return previousClose;
	}

	/**
	 * @param previousClose cierre anterior de la cotizacion mas reciente, o
	 *            {@code null} si esa cotizacion no lo declara
	 */
	public void setPreviousClose(BigDecimal previousClose) {
		this.previousClose = previousClose;
	}

}

