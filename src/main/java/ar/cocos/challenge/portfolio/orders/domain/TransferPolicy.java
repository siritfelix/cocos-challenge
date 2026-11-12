package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;

/**
 * Reglas de dominio de las transferencias de efectivo.
 *
 * <p>Las transferencias se modelan como ordenes sobre el instrumento de
 * efectivo {@code ARS} y cumplen un conjunto de reglas propias, distinto del
 * de las compras y ventas (spec FR-020, FR-022, FR-025):</p>
 * <ul>
 *   <li>Solo pueden operar sobre el instrumento de efectivo.</li>
 *   <li>Su tipo es siempre {@code MARKET}: se ejecutan de inmediato.</li>
 *   <li>No admiten precio: el peso no se cotiza y el precio almacenado queda
 *       vacio.</li>
 *   <li>El importe se expresa con la cantidad, nunca con el campo de importe,
 *       porque la columna {@code size} representa el importe en pesos.</li>
 *   <li>El importe debe ser mayor que cero.</li>
 * </ul>
 */
public final class TransferPolicy {

	private TransferPolicy() {
		// Clase de reglas sin estado.
	}

	/**
	 * Valida que una solicitud sea una transferencia de efectivo valida.
	 *
	 * @param instrument instrumento solicitado
	 * @param type tipo de ejecucion solicitado
	 * @param price precio informado, que debe ser nulo
	 * @param size cantidad o importe de la transferencia
	 * @param amount importe informado, que debe ser nulo
	 * @throws InvalidRequestException si alguna regla se incumple
	 */
	public static void validate(Instrument instrument, OrderType type, BigDecimal price, BigDecimal size,
			BigDecimal amount) {
		if (!instrument.isCash()) {
			throw new InvalidRequestException(
					"Cash transfers must use the '" + Instrument.CASH_TICKER + "' instrument");
		}
		if (type != OrderType.MARKET) {
			throw new InvalidRequestException("Cash transfers must be of type MARKET");
		}
		if (price != null) {
			throw new InvalidRequestException("Cash transfers must not provide a price");
		}
		if (amount != null) {
			throw new InvalidRequestException("Cash transfers must use 'size' to express the amount");
		}
		if (size == null || size.signum() <= 0) {
			throw new InvalidRequestException("Cash transfers require a positive amount");
		}
	}

}
