package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * Resumen del efectivo de una cuenta.
 *
 * <p>El efectivo total incluye las reservas; el disponible las descuenta
 * (spec FR-067, FR-068, BR-008, BR-009, BR-010).</p>
 *
 * @param totalCash efectivo total, incluidas las reservas
 * @param reservedCash efectivo comprometido por compras limite pendientes
 * @param availableCash efectivo disponible para operar
 */
public record CashSummary(BigDecimal totalCash, BigDecimal reservedCash, BigDecimal availableCash) {

	/**
	 * @param totalCash efectivo total
	 * @param reservedCash efectivo reservado
	 * @param availableCash efectivo disponible
	 */
	public CashSummary {
		totalCash = totalCash == null ? BigDecimal.ZERO : totalCash;
		reservedCash = reservedCash == null ? BigDecimal.ZERO : reservedCash;
		availableCash = availableCash == null ? BigDecimal.ZERO : availableCash;
	}

}
