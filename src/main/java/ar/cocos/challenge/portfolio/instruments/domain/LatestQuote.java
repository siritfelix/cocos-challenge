package ar.cocos.challenge.portfolio.instruments.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ultima cotizacion disponible de un instrumento.
 *
 * <p>La cotizacion mas reciente de un instrumento es la fila con mayor
 * {@code datetime}. El cierre es el precio de referencia para ejecutar
 * ordenes a mercado y para valuar posiciones; el cierre anterior permite
 * calcular el rendimiento diario (spec FR-027, FR-074).</p>
 *
 * @param close ultimo cierre disponible
 * @param previousClose cierre anterior de la misma cotizacion
 * @param datetime instante de la cotizacion
 */
public record LatestQuote(BigDecimal close, BigDecimal previousClose, LocalDateTime datetime) {

	/**
	 * Indica si el cierre es utilizable como precio de ejecucion y de valuacion.
	 *
	 * <p>Un cierre nulo, cero o negativo se considera no disponible: no se usa
	 * para ejecutar ordenes a mercado ni para valuar posiciones (spec FR-028).</p>
	 *
	 * @return {@code true} si el cierre es un precio valido
	 */
	public boolean isAvailable() {
		return close != null && close.signum() > 0;
	}

	/**
	 * Indica si el cierre anterior permite calcular el rendimiento diario.
	 *
	 * @return {@code true} si el cierre anterior es un valor valido
	 */
	public boolean hasPreviousClose() {
		return previousClose != null && previousClose.signum() != 0;
	}

}
