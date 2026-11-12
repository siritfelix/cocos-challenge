package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Calcula el costo promedio ponderado de adquisicion de una posicion.
 *
 * <p>El metodo de promedio ponderado (spec D-006, BR-015) se implementa como
 * un <em>fold ordenado</em> sobre las ordenes ejecutadas del par (cuenta,
 * instrumento):</p>
 * <pre>
 *   BUY  (q, p): qty  += q ; cost += q * p
 *   SELL (q)    : avg = cost / qty ; cost -= q * avg ; qty -= q
 * </pre>
 *
 * <p>No es un agregado de conjunto. Una venta reduce el costo al promedio
 * <em>vigente</em>, que depende de todas las ordenes anteriores, de modo que el
 * resultado no puede obtenerse con una consulta de sumas. Un ejemplo demuestra
 * la diferencia: si se compran 10 a 10, se venden 5, se compran 5 a 20, se venden
 * 10 y se compran 5 a 30, el promedio del fold es 30, mientras que el agregado
 * {@code totalCompra / totalComprado} daria 13,33 (research R-009).</p>
 *
 * <p>La precision se conserva completa durante el fold; el redondeo a dos
 * decimales ocurre en el borde de la API (spec D-050).</p>
 */
@Component
public class CostBasisCalculator {

	/**
	 * Precision de las divisiones internas.
	 */
	private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL128;

	/**
	 * Calcula el costo promedio ponderado de una posicion.
	 *
	 * @param executedTrades ordenes ejecutadas del par (cuenta, instrumento), en
	 *        orden cronologico
	 * @return el costo promedio por accion, o {@code null} si no hubo ninguna
	 *         compra
	 */
	public BigDecimal averageCost(List<ExecutedTrade> executedTrades) {
		BigDecimal quantity = BigDecimal.ZERO;
		BigDecimal cost = BigDecimal.ZERO;

		for (ExecutedTrade trade : executedTrades) {
			if (trade.isBuy()) {
				quantity = quantity.add(trade.size());
				cost = cost.add(trade.size().multiply(trade.price()));
				continue;
			}
			// Una venta reduce el costo al promedio vigente. Si no hay acciones
			// en la posicion, no hay costo que reducir.
			if (quantity.signum() <= 0) {
				continue;
			}
			BigDecimal average = cost.divide(quantity, DIVISION_PRECISION);
			BigDecimal removed = trade.size().multiply(average);
			cost = cost.subtract(removed);
			quantity = quantity.subtract(trade.size());
		}

		if (quantity.signum() <= 0) {
			return null;
		}
		return cost.divide(quantity, DIVISION_PRECISION);
	}

}
