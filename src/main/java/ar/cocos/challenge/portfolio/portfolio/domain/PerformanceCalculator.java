package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import org.springframework.stereotype.Component;

/**
 * Calcula los rendimientos de una posicion.
 *
 * <p>El rendimiento total mide la variacion del valor de mercado frente al
 * costo de adquisicion (spec FR-073) y el rendimiento diario la variacion del
 * cierre frente al cierre anterior (spec FR-074).</p>
 *
 * <p>Cuando falta un dato valido, el rendimiento se informa como
 * {@code null} en lugar de provocar una division por cero (spec FR-075).</p>
 */
@Component
public class PerformanceCalculator {

	/**
	 * Precision de las divisiones internas.
	 */
	private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL128;

	/**
	 * Escala con la que se informan los porcentajes.
	 */
	private static final int PERCENTAGE_SCALE = 2;

	/**
	 * Calcula el rendimiento total de una posicion.
	 *
	 * @param marketValue valor de mercado de la posicion
	 * @param averageCost costo promedio ponderado de adquisicion
	 * @param quantity cantidad total de acciones
	 * @return el rendimiento en porcentaje, o {@code null} si no hay costo de
	 *         adquisicion valido
	 */
	public BigDecimal totalPerformance(BigDecimal marketValue, BigDecimal averageCost, BigDecimal quantity) {
		if (averageCost == null || quantity == null) {
			return null;
		}
		BigDecimal acquisitionCost = averageCost.multiply(quantity);
		if (acquisitionCost.signum() == 0) {
			return null;
		}
		BigDecimal difference = marketValue.subtract(acquisitionCost);
		return percentage(difference, acquisitionCost);
	}

	/**
	 * Calcula el rendimiento diario de un instrumento.
	 *
	 * @param quote ultima cotizacion del instrumento
	 * @return el rendimiento en porcentaje, o {@code null} si no hay cierre
	 *         anterior valido
	 */
	public BigDecimal dailyPerformance(LatestQuote quote) {
		if (quote == null || !quote.hasPreviousClose()) {
			return null;
		}
		BigDecimal previousClose = quote.previousClose();
		BigDecimal difference = quote.close().subtract(previousClose);
		return percentage(difference, previousClose);
	}

	private BigDecimal percentage(BigDecimal difference, BigDecimal base) {
		return difference.divide(base, DIVISION_PRECISION).multiply(BigDecimal.valueOf(100))
				.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
	}

}
