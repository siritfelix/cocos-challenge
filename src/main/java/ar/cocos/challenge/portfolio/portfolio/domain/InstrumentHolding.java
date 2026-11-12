package ar.cocos.challenge.portfolio.portfolio.domain;

import java.math.BigDecimal;

/**
 * Cantidades de acciones de la cuenta en un instrumento.
 *
 * <p>La cantidad total surge de las compras y ventas ejecutadas; la reservada,
 * de las ventas limite pendientes (spec FR-070, BR-011, BR-012).</p>
 *
 * @param instrumentId identificador interno del instrumento
 * @param totalQuantity cantidad total de acciones
 * @param reservedQuantity cantidad comprometida por ventas limite pendientes
 */
public record InstrumentHolding(Long instrumentId, BigDecimal totalQuantity, BigDecimal reservedQuantity) {

	/**
	 * @param instrumentId identificador interno del instrumento
	 * @param totalQuantity cantidad total de acciones
	 * @param reservedQuantity cantidad reservada
	 */
	public InstrumentHolding {
		totalQuantity = totalQuantity == null ? BigDecimal.ZERO : totalQuantity;
		reservedQuantity = reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity;
	}

	/**
	 * @return la cantidad que puede operar la cuenta
	 */
	public BigDecimal availableQuantity() {
		return totalQuantity.subtract(reservedQuantity);
	}

}
