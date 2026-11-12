package ar.cocos.challenge.portfolio.orders.domain;

/**
 * Tipo de ejecucion de una orden.
 */
public enum OrderType {

	/**
	 * Se ejecuta de inmediato al precio de mercado (ultimo cierre disponible).
	 */
	MARKET,

	/**
	 * Queda pendiente en estado {@code NEW} hasta que se cancela.
	 */
	LIMIT;

}
