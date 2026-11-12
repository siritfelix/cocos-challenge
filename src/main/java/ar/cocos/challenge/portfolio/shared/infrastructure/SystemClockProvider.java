package ar.cocos.challenge.portfolio.shared.infrastructure;

import java.time.Clock;
import java.time.LocalDateTime;

import ar.cocos.challenge.portfolio.shared.application.ClockProvider;
import org.springframework.stereotype.Component;

/**
 * Implementacion de {@link ClockProvider} basada en el reloj del sistema en UTC.
 *
 * <p>La convencion UTC se aplica en toda la aplicacion: las fechas se
 * persisten en columnas {@code TIMESTAMP WITHOUT TIME ZONE} y se exponen sin
 * zona horaria (spec FR-046).</p>
 */
@Component
public class SystemClockProvider implements ClockProvider {

	@Override
	public LocalDateTime now() {
		return LocalDateTime.now(Clock.systemUTC());
	}

}
