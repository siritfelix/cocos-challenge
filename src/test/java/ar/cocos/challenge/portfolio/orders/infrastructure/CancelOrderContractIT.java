package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Pruebas funcionales de la cancelacion de ordenes.
 *
 * <p>Cubren los escenarios 26 a 32 de la seccion 18 de la spec: cancelacion de
 * una orden pendiente, rechazo de los estados terminales, orden inexistente y
 * orden de otra cuenta.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CancelOrderContractIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	/**
	 * Otra cuenta, usada para verificar que no se revelan ordenes ajenas.
	 */
	private static final String OTHER_ACCOUNT = "10002";

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
	@DisplayName("Cancelar una orden pendiente devuelve 200 con estado CANCELLED")
	void cancelsPendingOrder() throws Exception {
		long orderId = createLimitOrder(ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.rejectionReason").doesNotExist());
	}

	@Test
	@DisplayName("Cancelar una orden ya ejecutada devuelve 409")
	void rejectsCancellingFilledOrder() throws Exception {
		transfer(ACCOUNT, "CASH_IN", "50000.00");
		long orderId = createMarketOrder(ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("Cancelar una orden rechazada devuelve 409")
	void rejectsCancellingRejectedOrder() throws Exception {
		// Una compra sin efectivo queda REJECTED.
		long orderId = createRejectedOrder(ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("Cancelar una orden ya cancelada devuelve 409")
	void rejectsCancellingCancelledOrder() throws Exception {
		long orderId = createLimitOrder(ACCOUNT);
		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("Cancelar una orden inexistente devuelve 404")
	void rejectsUnknownOrder() throws Exception {
		mockMvc.perform(delete("/api/v1/orders/{orderId}", 999999)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Cancelar una orden de otra cuenta devuelve 404 sin revelar su existencia")
	void rejectsOrderOfAnotherAccount() throws Exception {
		long orderId = createLimitOrder(OTHER_ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Cancelar una transferencia devuelve 409")
	void rejectsCancellingTransfer() throws Exception {
		transfer(ACCOUNT, "CASH_IN", "10000.00");
		Long orderId = lastOrderId();

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("Rechaza con 400 la ausencia de la cabecera de cuenta")
	void rejectsMissingAccountHeader() throws Exception {
		long orderId = createLimitOrder(ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 404 una cuenta inexistente")
	void rejectsUnknownAccount() throws Exception {
		long orderId = createLimitOrder(ACCOUNT);

		mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
				.header("X-AccountNumber", "99999"))
				.andExpect(status().isNotFound());
	}

	private long createLimitOrder(String account) throws Exception {
		// Se Ingiera efectivo para que la orden limite quede pendiente: sin
		// recursos suficientes la orden se rechaza y no seria cancelable.
		transfer(account, "CASH_IN", "50000.00");
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));
		return lastOrderId();
	}

	private long createMarketOrder(String account) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FILLED"));
		return lastOrderId();
	}

	private long createRejectedOrder(String account) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":100000}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"));
		return lastOrderId();
	}

	private void transfer(String account, String side, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"%s","type":"MARKET","size":%s}
						""".formatted(side, amount)))
				.andExpect(status().isCreated());
	}

	private long lastOrderId() {
		Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM orders", Long.class);
		return id == null ? 0L : id;
	}

}
