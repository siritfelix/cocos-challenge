package ar.cocos.challenge.portfolio.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del calculo del costo promedio ponderado.
 *
 * <p>El caso intercalado es el contraejemplo que demuestra que el costo
 * promedio NO puede obtenerse con un agregado de conjunto (research R-009):
 * el fold da 30 y el agregado daria 13,33.</p>
 */
class CostBasisCalculatorTest {

	private final CostBasisCalculator calculator = new CostBasisCalculator();

	@Test
	@DisplayName("Sin compras no hay costo de adquisicion")
	void returnsNullWithoutBuys() {
		assertThat(calculator.averageCost(List.of())).isNull();
	}

	@Test
	@DisplayName("Una sola compra define el costo promedio")
	void averagesSingleBuy() {
		List<ExecutedTrade> trades = List.of(buy("10", "100.00"));

		assertThat(calculator.averageCost(trades)).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("El costo promedio pondera por cantidad, no por numero de ordenes")
	void weightsAverageByQuantity() {
		List<ExecutedTrade> trades = List.of(buy("10", "100.00"), buy("30", "200.00"));

		// (10*100 + 30*200) / 40 = 175
		assertThat(calculator.averageCost(trades)).isEqualByComparingTo("175.00");
	}

	@Test
	@DisplayName("Una venta reduce el costo al promedio vigente")
	void saleRemovesCostAtCurrentAverage() {
		List<ExecutedTrade> trades = List.of(buy("10", "100.00"), sell("5", "150.00"));

		// Tras comprar 10 a 100, el costo es 1000. Vender 5 a costo promedio
		// retira 5*100 = 500; quedan 5 acciones con costo 500.
		assertThat(calculator.averageCost(trades)).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("El caso intercalado demuestra que el promedio no es un agregado")
	void interleavedCaseProvesItIsNotAnAggregate() {
		List<ExecutedTrade> trades = List.of(
				buy("10", "10.00"),
				sell("5", "12.00"),
				buy("5", "20.00"),
				sell("10", "25.00"),
				buy("5", "30.00"));

		// El fold da 30. El agregado (total comprado / total comprado) daria
		// (100 + 100 + 150) / 20 = 17,50, que es incorrecto.
		assertThat(calculator.averageCost(trades)).isEqualByComparingTo("30.00");
	}

	@Test
	@DisplayName("Una posicion cerrada no tiene costo de adquisicion")
	void returnsNullWhenPositionIsClosed() {
		List<ExecutedTrade> trades = List.of(buy("10", "100.00"), sell("10", "150.00"));

		assertThat(calculator.averageCost(trades)).isNull();
	}

	@Test
	@DisplayName("Una venta sin posicion previa no produce division por cero")
	void handlesSaleWithoutPosition() {
		List<ExecutedTrade> trades = List.of(sell("5", "100.00"), buy("10", "100.00"));

		assertThat(calculator.averageCost(trades)).isEqualByComparingTo("100.00");
	}

	private ExecutedTrade buy(String size, String price) {
		return new ExecutedTrade(ExecutedTrade.TradeSide.BUY, new BigDecimal(size), new BigDecimal(price));
	}

	private ExecutedTrade sell(String size, String price) {
		return new ExecutedTrade(ExecutedTrade.TradeSide.SELL, new BigDecimal(size), new BigDecimal(price));
	}

}
