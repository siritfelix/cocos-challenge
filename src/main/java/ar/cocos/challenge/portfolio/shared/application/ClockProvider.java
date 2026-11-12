package ar.cocos.challenge.portfolio.shared.application;

import java.time.LocalDateTime;

/**
 * Provee la hora actual en UTC.
 *
 * <p>Abstraer el reloj permite que los tests fijen la hora de las ordenes de
 * forma determinista y garantiza la convencion UTC de toda la aplicacion
 * (spec FR-046).</p>
 */
public interface ClockProvider {

	/**
	 * @return el instante actual en UTC, sin zona horaria asociada
	 */
	LocalDateTime now();

}
