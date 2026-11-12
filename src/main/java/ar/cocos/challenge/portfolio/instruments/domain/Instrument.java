package ar.cocos.challenge.portfolio.instruments.domain;

/**
 * Instrumento del catalogo.
 *
 * @param id identificador interno del instrumento
 * @param ticker simbolo con el que se identifica al instrumento
 * @param name nombre descriptivo del instrumento
 * @param type tipo del instrumento
 */
public record Instrument(Long id, String ticker, String name, InstrumentType type) {

	/**
	 * Ticker del instrumento que representa el efectivo.
	 */
	public static final String CASH_TICKER = "ARS";

	/**
	 * @return {@code true} si el instrumento es la moneda de efectivo
	 */
	public boolean isCash() {
		return CASH_TICKER.equals(ticker);
	}

	/**
	 * @return {@code true} si el instrumento admite compras y ventas
	 */
	public boolean isTradeable() {
		return type == InstrumentType.ACCIONES;
	}

}
