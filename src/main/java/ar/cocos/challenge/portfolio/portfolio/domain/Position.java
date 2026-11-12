package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * Posicion de la cuenta en un instrumento.
 *
 * <p>Es una vista derivada de las ordenes ejecutadas y pendientes de la
 * cuenta (spec FR-069, FR-070).</p>
 *
 * @param ticker simbolo del instrumento
 * @param totalQuantity cantidad total de acciones en la posicion
 * @param availableQuantity cantidad que puede operar la cuenta
 * @param reservedQuantity cantidad comprometida por ventas limite pendientes
 * @param currentPrice ultimo cierre disponible del instrumento
 * @param marketValue valor de mercado de la posicion
 * @param averageCost costo promedio ponderado de adquisicion
 * @param totalPerformance rendimiento total en porcentaje, o {@code null} si no
 *        hay costo de adquisicion valido
 * @param dailyPerformance rendimiento diario en porcentaje, o {@code null} si no
 *        hay cierre anterior valido
 */
public record Position(
		String ticker,
		BigDecimal totalQuantity,
		BigDecimal availableQuantity,
		BigDecimal reservedQuantity,
		BigDecimal currentPrice,
		BigDecimal marketValue,
		BigDecimal averageCost,
		BigDecimal totalPerformance,
		BigDecimal dailyPerformance) {

	/**
	 * @param ticker simbolo del instrumento
	 * @param totalQuantity cantidad total de acciones
	 * @param availableQuantity cantidad disponible
	 * @param reservedQuantity cantidad reservada
	 * @param currentPrice ultimo cierre disponible
	 * @param marketValue valor de mercado
	 * @param averageCost costo promedio ponderado
	 */
	public Position {
		totalQuantity = totalQuantity == null ? BigDecimal.ZERO : totalQuantity;
		availableQuantity = availableQuantity == null ? BigDecimal.ZERO : availableQuantity;
		reservedQuantity = reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity;
		marketValue = marketValue == null ? BigDecimal.ZERO : marketValue;
	}

}
