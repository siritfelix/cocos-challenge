package ar.cocos.challenge.portfolio.instruments.domain;

/**
 * Tipo de instrumento del catalogo.
 *
 * <p>Solo admite acciones y moneda (spec D-048). Las compras y ventas solo
 * pueden hacerse sobre acciones; la moneda corresponde al efectivo
 * (instrumento {@code ARS}).</p>
 */
public enum InstrumentType {

	/**
	 * Accion negociable.
	 */
	ACCIONES,

	/**
	 * Moneda. En este catalogo solo la representa el instrumento de efectivo.
	 */
	MONEDA

}
