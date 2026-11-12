package ar.cocos.challenge.portfolio.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueba de concurrencia de la idempotencia.
 *
 * <p>Dos peticiones simultaneas con la misma {@code Idempotency-Key} deben
 * producir una sola orden: el lock pesimista de la cuenta serializa a las dos
 * transacciones y la segunda encuentra la clave ya registrada, de modo que
 * reproduce el resultado original en lugar de duplicar la orden (spec FR-057,
 * escenario 19; SC-008).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentIdempotencyIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetOrders() {
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	@DisplayName("Dos peticiones simultaneas con la misma clave producen una sola orden")
	void sameKeyConcurrentlyProducesOneOrder() throws Exception {
		String base = "http://localhost:" + port;
		ConcurrentHttp.Response transfer = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"ARS\",\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"size\":100000.00}");
		assertThat(transfer.status()).isEqualTo(201);

		String sharedKey = UUID.randomUUID().toString();
		List<ConcurrentHttp.Response> results = ConcurrentHttp.runSimultaneously(2, () ->
				ConcurrentHttp.post(base, ACCOUNT, sharedKey,
						"{\"ticker\":\"YPFD\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"size\":1,\"price\":100.00}"));

		assertThat(results).hasSize(2);
		String firstOrderId = results.get(0).text("id");
		String secondOrderId = results.get(1).text("id");
		assertThat(firstOrderId)
				.as("ambas respuestas corresponden a la misma orden")
				.isEqualTo(secondOrderId);
		assertThat(results)
				.as("las dos peticiones concluyen satisfactoriamente: la segunda es una repeticion")
				.allSatisfy(result -> assertThat(result.status()).isEqualTo(201));

		Long orderCount = jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
		Long keyCount = jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class);
		assertThat(orderCount)
				.as("la transferencia mas UNA sola compra: no hay duplicados")
				.isEqualTo(2L);
		assertThat(keyCount)
				.as("una clave por orden: la clave compartida se registra una sola vez")
				.isEqualTo(2L);
	}

}
