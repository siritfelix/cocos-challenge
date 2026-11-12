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
 * Prueba de concurrencia de compras que compiten por el mismo efectivo.
 *
 * <p>Dos compras simultaneas, cada una individualmente accesible, compiten por
 * el mismo saldo: el lock pesimista de la cuenta las serializa y solo la
 * primera encuentra efectivo suficiente. La segunda se rechaza por regla de
 * negocio, nunca por un error tecnico (spec FR-094, escenario 20; SC-008).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentBuyIT {

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
	@DisplayName("Dos compras simultaneas por el mismo efectivo: como maximo una se acepta")
	void onlyOneConcurrentBuyIsAccepted() throws Exception {
		String base = "http://localhost:" + port;
		ConcurrentHttp.Response transfer = ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
				"{\"ticker\":\"ARS\",\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"size\":1000.00}");
		assertThat(transfer.status()).isEqualTo(201);

		// Cada compra cuesta 700, accesible por separado contra 1000 de
		// efectivo, pero las dos juntas exceden el saldo.
		List<ConcurrentHttp.Response> results = ConcurrentHttp.runSimultaneously(2, () ->
				ConcurrentHttp.post(base, ACCOUNT, UUID.randomUUID().toString(),
						"{\"ticker\":\"YPFD\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"size\":1,\"price\":700.00}"));

		long accepted = results.stream()
				.filter(result -> "NEW".equals(result.text("status")))
				.count();
		long rejected = results.stream()
				.filter(result -> "REJECTED".equals(result.text("status")))
				.count();
		assertThat(accepted)
				.as("como maximo una compra acepta, porque el lock serializa y el saldo no alcanza para dos")
				.isEqualTo(1);
		assertThat(rejected)
				.as("la compra que no alcanza el efectivo se rechaza por regla de negocio")
				.isEqualTo(1);
		assertThat(results)
				.as("el motivo del rechazo es la falta de efectivo")
				.anySatisfy(result -> assertThat(result.text("rejectionReason"))
						.isEqualTo("Insufficient available cash balance"));

		// La suma de las aceptadas (700) no excede el saldo inicial (1000) y el
		// efectivo disponible nunca queda negativo.
		ConcurrentHttp.Response portfolio = ConcurrentHttp.get(base, "/api/v1/portfolio", ACCOUNT);
		assertThat(portfolio.status()).isEqualTo(200);
		BigDecimal reservedCash = portfolio.body().path("reservedCash").decimalValue();
		BigDecimal availableCash = portfolio.body().path("availableCash").decimalValue();
		assertThat(availableCash).isEqualByComparingTo("300.00");
		assertThat(reservedCash).isEqualByComparingTo("700.00");
		assertThat(availableCash).isGreaterThanOrEqualTo(BigDecimal.ZERO);
	}

}
