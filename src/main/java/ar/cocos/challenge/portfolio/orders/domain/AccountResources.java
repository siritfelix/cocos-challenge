package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;

/**
 * Recursos disponibles de una cuenta en un instante dado.
 *
 * <p>Concentra el efectivo y las acciones disponibles, ya separadas de las
 * reservas de las ordenes pendientes (spec FR-033, FR-034).</p>
 *
 * @param totalCash efectivo total de la cuenta, incluidas las reservas
 * @param reservedCash efectivo comprometido por compras limite pendientes
 * @param availableCash efectivo que puede ser usado en nuevas operaciones
 * @param availableShares acciones disponibles del instrumento
 */
public record AccountResources(BigDecimal totalCash, BigDecimal reservedCash, BigDecimal availableCash,
		BigDecimal availableShares) {

	/**
	 * @param totalCash efectivo total
	 * @param reservedCash efectivo reservado
	 * @param availableCash efectivo disponible
	 * @param availableShares acciones disponibles
	 */
	public AccountResources {
		totalCash = totalCash == null ? BigDecimal.ZERO : totalCash;
		reservedCash = reservedCash == null ? BigDecimal.ZERO : reservedCash;
		availableCash = availableCash == null ? BigDecimal.ZERO : availableCash;
		availableShares = availableShares == null ? BigDecimal.ZERO : availableShares;
	}

}
