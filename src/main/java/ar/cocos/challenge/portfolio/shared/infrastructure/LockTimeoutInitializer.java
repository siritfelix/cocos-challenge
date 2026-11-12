package ar.cocos.challenge.portfolio.shared.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Acota el tiempo de espera por un bloqueo de fila.
 *
 * <p>El hint {@code jakarta.persistence.lock.timeout} de Hibernate 7 aplica el
 * timeout DESPUES de la sentencia de bloqueo, por lo que no acota la espera
 * real (research R-006, bug spring-data-jpa#4244). Por eso el limite se
 * emite con {@code set_config('lock_timeout', ..., true)}, que es la forma
 * funcional de {@code SET LOCAL} y sí acota la espera desde la primera
 * sentencia de la transaccion.</p>
 *
 * <p>Debe invocarse como PRIMERA operacion de la transaccion, antes de
 * cualquier lectura de saldos o posiciones.</p>
 */
@Component
public class LockTimeoutInitializer {

	/**
	 * Tiempo maximo de espera por un bloqueo, en milisegundos.
	 */
	private static final String LOCK_TIMEOUT_MILLIS = "5000";

	private final JdbcTemplate jdbcTemplate;

	/**
	 * @param jdbcTemplate plantilla JDBC para emitir la sentencia de configuracion
	 */
	public LockTimeoutInitializer(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Emite el limite de espera de bloqueo para la transaccion en curso.
	 *
	 * <p>Usa la variante {@code is_local = true}, por lo que el limite solo
	 * aplica a la transaccion actual y se restaura al finalizar.</p>
	 */
	public void apply() {
		jdbcTemplate.execute("SELECT set_config('lock_timeout', '" + LOCK_TIMEOUT_MILLIS + "', true)");
	}

}
