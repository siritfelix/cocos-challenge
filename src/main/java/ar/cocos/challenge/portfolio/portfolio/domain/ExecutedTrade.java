package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * Operacion ejecutada sobre acciones que participa en el calculo del costo
 * promedio ponderado de una posicion.
 *
 * <p>Las operaciones se recorren en orden cronologico; el desempate por
 * identificador garantiza que dos lecturas concurrentes obtengan la misma
 * secuencia (research R-009).</p>
 *
 * @param side lado de la operacion
 * @param size cantidad de acciones
 * @param price precio de ejecucion
 */
public record ExecutedTrade(TradeSide side, BigDecimal size, BigDecimal price) {

	/**
	 * @return {@code true} si la operacion es una compra
	 */
	public boolean isBuy() {
		return side == TradeSide.BUY;
	}

	/**
	 * Lado de una operacion ejecutada, limitado a los lados de acciones.
	 */
	public enum TradeSide {

		/**
		 * Compra.
		 */
		BUY,

		/**
		 * Venta.
		 */
		SELL

	}

}
