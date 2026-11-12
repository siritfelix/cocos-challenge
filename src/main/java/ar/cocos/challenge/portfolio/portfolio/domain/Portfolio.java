package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estado patrimonial de la cuenta.
 *
 * <p>El patrimonio total suma el efectivo total y el valor de mercado de las
 * posiciones (spec BR-013). Las ordenes pendientes no alteran el patrimonio
 * total: solo reducen la disponibilidad (spec FR-077, D-040).</p>
 *
 * @param totalCash efectivo total de la cuenta, incluidas las reservas
 * @param availableCash efectivo disponible para operar
 * @param reservedCash efectivo comprometido por compras limite pendientes
 * @param totalValue patrimonio total de la cuenta
 * @param positions posiciones en instrumentos
 */
public record Portfolio(
		BigDecimal totalCash,
		BigDecimal availableCash,
		BigDecimal reservedCash,
		BigDecimal totalValue,
		List<Position> positions) {

	/**
	 * @param totalCash efectivo total
	 * @param availableCash efectivo disponible
	 * @param reservedCash efectivo reservado
	 * @param totalValue patrimonio total
	 * @param positions posiciones en instrumentos
	 */
	public Portfolio {
		totalCash = totalCash == null ? BigDecimal.ZERO : totalCash;
		availableCash = availableCash == null ? BigDecimal.ZERO : availableCash;
		reservedCash = reservedCash == null ? BigDecimal.ZERO : reservedCash;
		totalValue = totalValue == null ? BigDecimal.ZERO : totalValue;
		positions = List.copyOf(positions);
	}

}
