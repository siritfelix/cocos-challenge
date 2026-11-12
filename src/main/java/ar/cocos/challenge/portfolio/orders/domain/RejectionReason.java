package ar.cocos.challenge.portfolio.orders.domain;

/**
 * Motivo por el que una orden fue rechazada por una regla de negocio.
 *
 * <p>El motivo se persiste junto a la orden para que el usuario pueda
 * entender por que su operacion no se ejecuto (spec FR-042, FR-092).</p>
 */
public enum RejectionReason {

	/**
	 * El efectivo disponible no cubre el costo de la operacion.
	 */
	INSUFFICIENT_CASH("Insufficient available cash balance"),

	/**
	 * No hay acciones disponibles suficientes para la venta.
	 */
	INSUFFICIENT_SHARES("Insufficient available shares"),

	/**
	 * El importe indicado no alcanza para operar una sola accion.
	 */
	AMOUNT_TOO_SMALL("Amount is not enough to operate a single share"),

	/**
	 * El instrumento no tiene una cotizacion disponible para operar a mercado.
	 */
	NO_MARKET_DATA("No market data available for the instrument");

	private final String message;

	RejectionReason(String message) {
		this.message = message;
	}

	/**
	 * @return el mensaje legible del motivo
	 */
	public String message() {
		return message;
	}

}
