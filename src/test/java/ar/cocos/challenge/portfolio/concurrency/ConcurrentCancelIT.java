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
 * Prueba de concurrencia de la cancelacion.
 *
 * <p>Dos cancelaciones simultaneas de la misma orden pendiente deben producir
 * una sola cancelacion efectiva: la primera devuelve {@code 200} con la orden
 * cancelada y la segunda, que encuentra la orden ya en estado terminal,
 * devuelve {@code 409} (spec §9, escenario 25; SC-008).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentCancelIT {

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
	@DisplayName("Dos cancelaciones simultaneas: una sola efectiva y un solo 409")
	void concurrentCancelsProduceOneEffectiveCancel() throws Exception {
		String base = "http://localhost:" + port;
		ConcurrentHttp.Response transfer = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"ARS\",\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"size\":1000.00}");
		assertThat(transfer.status()).isEqualTo(201);
		ConcurrentHttp.Response pending = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"YPFD\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"size\":1,\"price\":100.00}");
		assertThat(pending.status()).isEqualTo(201);
		assertThat(pending.text("status")).isEqualTo("NEW");
		long orderId = pending.body().path("id").asLong();

		List<ConcurrentHttp.Response> results = ConcurrentHttp.runSimultaneously(2, () ->
				ConcurrentHttp.delete(base, ACCOUNT, orderId));

		assertThat(results).hasSize(2);
		long ok = results.stream().filter(result -> result.status() == 200).count();
		long conflict = results.stream().filter(result -> result.status() == 409).count();
		assertThat(ok)
				.as("exactamente una cancelacion es efectiva")
				.isEqualTo(1);
		assertThat(conflict)
				.as("la segunda cancelacion encuentra la orden ya cancelada y responde 409")
				.isEqualTo(1);
		assertThat(results.stream().filter(result -> result.status() == 200))
				.as("la cancelacion efectiva devuelve la orden en estado CANCELLED")
				.allSatisfy(result -> assertThat(result.text("status")).isEqualTo("CANCELLED"));

		Long cancelledCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM orders WHERE id = ? AND status = 'CANCELLED'", Long.class, orderId);
		assertThat(cancelledCount)
				.as("la orden queda cancelada una sola vez en la base")
				.isEqualTo(1L);
	}

}
