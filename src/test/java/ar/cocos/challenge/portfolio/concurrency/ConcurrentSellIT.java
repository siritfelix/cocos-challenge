package ar.cocos.challenge.portfolio.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * Prueba de concurrencia de ventas que compiten por las mismas acciones.
 *
 * <p>Dos ventas simultaneas, cada una individualmente cubierta, compiten por
 * las mismas acciones: el lock pesimista de la cuenta las serializa y solo la
 * primera encuentra acciones suficientes. La segunda se rechaza, y la cantidad
 * de acciones de la posicion nunca queda negativa (spec FR-094, escenario 21;
 * SC-008).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentSellIT {

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
	@DisplayName("Dos ventas simultaneas por las mismas acciones: como maximo una se acepta")
	void onlyOneConcurrentSellIsAccepted() throws Exception {
		String base = "http://localhost:" + port;
		ConcurrentHttp.Response transfer = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"ARS\",\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"size\":100000.00}");
		assertThat(transfer.status()).isEqualTo(201);
		ConcurrentHttp.Response buy = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"YPFD\",\"side\":\"BUY\",\"type\":\"MARKET\",\"size\":2}");
		assertThat(buy.status()).isEqualTo(201);
		assertThat(buy.text("status")).isEqualTo("FILLED");

		// Cada venta entrega 2 acciones, cubierta por separado contra una
		// posicion de 2, pero las dos juntas necesitan 4.
		List<ConcurrentHttp.Response> results = ConcurrentHttp.runSimultaneously(2, () ->
				ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
						"{\"ticker\":\"YPFD\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"size\":2,\"price\":1.00}"));

		long accepted = results.stream()
				.filter(result -> "NEW".equals(result.text("status")))
				.count();
		long rejected = results.stream()
				.filter(result -> "REJECTED".equals(result.text("status")))
				.count();
		assertThat(accepted)
				.as("como maximo una venta se acepta, porque el lock serializa y la posicion no alcanza para dos")
				.isEqualTo(1);
		assertThat(rejected)
				.as("la venta que no tiene acciones se rechaza por regla de negocio")
				.isEqualTo(1);
		assertThat(results)
				.as("el motivo del rechazo es la falta de acciones")
				.anySatisfy(result -> assertThat(result.text("rejectionReason"))
						.isEqualTo("Insufficient available shares"));

		// La posicion conserva las 2 acciones y las reservadas son las de la
		// venta aceptada: la cantidad disponible nunca queda negativa.
		ConcurrentHttp.Response portfolio = ConcurrentHttp.get(base, "/api/v1/portfolio", ACCOUNT);
		assertThat(portfolio.status()).isEqualTo(200);
		assertThat(portfolio.body().path("positions").isArray()).isTrue();
		assertThat(portfolio.body().path("positions").size()).isEqualTo(1);
		assertThat(portfolio.body().path("positions").get(0).path("totalQuantity").decimalValue())
				.isEqualByComparingTo("2.00");
		assertThat(portfolio.body().path("positions").get(0).path("reservedQuantity").decimalValue())
				.isEqualByComparingTo("2.00");
		assertThat(portfolio.body().path("positions").get(0).path("availableQuantity").decimalValue())
				.isEqualByComparingTo("0.00");
		assertThat(portfolio.body().path("positions").get(0).path("totalQuantity").decimalValue())
				.isGreaterThanOrEqualTo(BigDecimal.ZERO);
	}

}
