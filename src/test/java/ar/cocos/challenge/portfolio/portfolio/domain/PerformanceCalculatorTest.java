package ar.cocos.challenge.portfolio.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del calculo de rendimientos.
 *
 * <p>Cubren las formulas de la spec FR-073, FR-074 y las guardas de division
 * por cero de FR-075.</p>
 */
class PerformanceCalculatorTest {

	private final PerformanceCalculator calculator = new PerformanceCalculator();

	@Test
	@DisplayName("El rendimiento total mide la variacion frente al costo de adquisicion")
	void computesTotalPerformance() {
		// Valor de mercado 2500, costo de adquisicion 2000 (10 acciones a 200).
		BigDecimal performance = calculator.totalPerformance(new BigDecimal("2500.00"),
				new BigDecimal("200.00"), new BigDecimal("10"));

		// (2500 - 2000) / 2000 * 100 = 25
		assertThat(performance).isEqualByComparingTo("25.00");
	}

	@Test
	@DisplayName("Un rendimiento negativo se informa con signo negativo")
	void computesNegativePerformance() {
		BigDecimal performance = calculator.totalPerformance(new BigDecimal("1500.00"),
				new BigDecimal("200.00"), new BigDecimal("10"));

		assertThat(performance).isEqualByComparingTo("-25.00");
	}

	@Test
	@DisplayName("Sin costo de adquisicion no hay rendimiento total")
	void returnsNullWithoutAverageCost() {
		BigDecimal performance = calculator.totalPerformance(new BigDecimal("2500.00"), null,
				new BigDecimal("10"));

		assertThat(performance).isNull();
	}

	@Test
	@DisplayName("Un costo de adquisicion cero no divide por cero")
	void returnsNullWithZeroAcquisitionCost() {
		BigDecimal performance = calculator.totalPerformance(new BigDecimal("2500.00"), BigDecimal.ZERO,
				new BigDecimal("10"));

		assertThat(performance).isNull();
	}

	@Test
	@DisplayName("El rendimiento diario mide la variacion del cierre")
	void computesDailyPerformance() {
		LatestQuote quote = new LatestQuote(new BigDecimal("110.00"), new BigDecimal("100.00"),
				LocalDateTime.of(2026, 11, 12, 0, 0));

		// (110 - 100) / 100 * 100 = 10
		assertThat(calculator.dailyPerformance(quote)).isEqualByComparingTo("10.00");
	}

	@Test
	@DisplayName("Sin cotizacion no hay rendimiento diario")
	void returnsNullWithoutQuote() {
		assertThat(calculator.dailyPerformance(null)).isNull();
	}

	@Test
	@DisplayName("Un cierre anterior cero no divide por cero")
	void returnsNullWithZeroPreviousClose() {
		LatestQuote quote = new LatestQuote(new BigDecimal("110.00"), BigDecimal.ZERO,
				LocalDateTime.of(2026, 11, 12, 0, 0));

		assertThat(calculator.dailyPerformance(quote)).isNull();
	}

	@Test
	@DisplayName("Un cierre anterior negativo no divide por cero")
	void returnsNullWithNegativePreviousClose() {
		LatestQuote quote = new LatestQuote(new BigDecimal("110.00"), new BigDecimal("-1.00"),
				LocalDateTime.of(2026, 11, 12, 0, 0));

		assertThat(calculator.dailyPerformance(quote)).isNull();
	}

}
