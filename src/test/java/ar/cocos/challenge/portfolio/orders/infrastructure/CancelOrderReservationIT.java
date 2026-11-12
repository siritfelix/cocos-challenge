package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas funcionales de la liberacion de reservas al cancelar una orden.
 *
 * <p>Cubren el escenario 27 de la seccion 18 de la spec: al cancelar una orden
 * limite, los recursos que tenia comprometidos vuelven a estar disponibles
 * (spec FR-063, FR-037).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CancelOrderReservationIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetOrders() {
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	@DisplayName("Cancelar una compra limite libera el efectivo reservado")
	void cancellingBuyLimitReleasesCash() throws Exception {
		transfer("CASH_IN", "100000.00");
		long orderId = createLimitBuy("10000.00");

		mockMvc.perform(get("/api/v1/portfolio").header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reservedCash").value(10000.00))
				.andExpect(jsonPath("$.availableCash").value(90000.00));

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		// Tras cancelar, la reserva se libera: el efectivo disponible vuelve a
		// ser el total.
		mockMvc.perform(get("/api/v1/portfolio").header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reservedCash").value(0.00))
				.andExpect(jsonPath("$.availableCash").value(100000.00))
				.andExpect(jsonPath("$.totalCash").value(100000.00))
				.andExpect(jsonPath("$.totalValue").value(100000.00));
	}

	@Test
	@DisplayName("Cancelar una venta limite libera las acciones reservadas")
	void cancellingSellLimitReleasesShares() throws Exception {
		transfer("CASH_IN", "100000.00");
		createMarketBuy("YPFD", "5");
		long orderId = createLimitSell("YPFD", "2", "9000.00");

		mockMvc.perform(get("/api/v1/portfolio").header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions[0].totalQuantity").value(5.00))
				.andExpect(jsonPath("$.positions[0].reservedQuantity").value(2.00))
				.andExpect(jsonPath("$.positions[0].availableQuantity").value(3.00));

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		// Tras cancelar, las acciones vuelven a estar disponibles y la
		// posicion conserva la cantidad total.
		mockMvc.perform(get("/api/v1/portfolio").header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions[0].totalQuantity").value(5.00))
				.andExpect(jsonPath("$.positions[0].reservedQuantity").value(0.00))
				.andExpect(jsonPath("$.positions[0].availableQuantity").value(5.00));
	}

	@Test
	@DisplayName("Una orden cancelada no computa como reserva en el historial")
	void cancelledOrderIsNotPending() throws Exception {
		transfer("CASH_IN", "100000.00");
		long orderId = createLimitBuy("10000.00");

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "NEW"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "CANCELLED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	private void transfer(String side, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"%s","type":"MARKET","size":%s}
						""".formatted(side, amount)))
				.andExpect(status().isCreated());
	}

	private void createMarketBuy(String ticker, String size) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"BUY","type":"MARKET","size":%s}
						""".formatted(ticker, size)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FILLED"));
	}

	private long createLimitBuy(String price) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":%s}
						""".formatted(price)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));
		return lastOrderId();
	}

	private long createLimitSell(String ticker, String size, String price) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"SELL","type":"LIMIT","size":%s,"price":%s}
						""".formatted(ticker, size, price)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));
		return lastOrderId();
	}

	private long lastOrderId() {
		Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM orders", Long.class);
		return id == null ? 0L : id;
	}

}
