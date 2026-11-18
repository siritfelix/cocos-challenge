package ar.cocos.challenge.portfolio.orders.infrastructure;

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
 * Pruebas funcionales del endpoint {@code POST /api/v1/orders}.
 *
 * <p>Cubren los escenarios 9 a 15, 23 a 25 y 33 a 35 de la seccion 18 de la
 * spec: ordenes a mercado y limite, rechazos por regla de negocio persistidos
 * como {@code REJECTED} y errores de validacion del request, que no persisten
 * ninguna orden.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CreateOrderContractIT {

	/**
	 * Cuenta de pruebas con efectivo disponible.
	 */
	private static final String FUNDED_ACCOUNT = "10001";

	/**
	 * Cuenta de pruebas sin recursos, usada para verificar rechazos.
	 */
	private static final String EMPTY_ACCOUNT = "10004";

	/**
	 * Instrumento sin ninguna cotizacion, para verificar el rechazo por
	 * ausencia de mercado.
	 */
	private static final String INSTRUMENT_WITHOUT_QUOTES = "IRCP";

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
	@DisplayName("Una compra a mercado con recursos suficientes queda FILLED")
	void createsFilledMarketBuy() throws Exception {
		fundAccount(FUNDED_ACCOUNT, "50000");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.ticker").value("YPFD"))
				.andExpect(jsonPath("$.side").value("BUY"))
				.andExpect(jsonPath("$.type").value("MARKET"))
				.andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.rejectionReason").doesNotExist())
				.andExpect(jsonPath("$.datetime").exists());
	}

	@Test
	@DisplayName("Una compra a mercado sin efectivo suficiente queda REJECTED con motivo")
	void rejectsMarketBuyWithoutCash() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", EMPTY_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":100000}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());
	}

	@Test
	@DisplayName("Una orden limite con recursos suficientes queda NEW")
	void createsPendingLimitBuy() throws Exception {
		fundAccount(FUNDED_ACCOUNT, "50000");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":20000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.price").value(20000.00));
	}

	@Test
	@DisplayName("Una orden limite sin efectivo suficiente queda REJECTED")
	void rejectsLimitBuyWithoutCash() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", EMPTY_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":10,"price":20000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());
	}

	@Test
	@DisplayName("Una compra a mercado sobre un instrumento sin cotizacion queda REJECTED")
	void rejectsMarketBuyWithoutMarketData() throws Exception {
		fundAccount(FUNDED_ACCOUNT, "50000");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"IRCP","side":"BUY","type":"MARKET","size":1}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());
	}

	@Test
	@DisplayName("Una compra por importe calcula la cantidad maxima de acciones")
	void createsBuyByAmount() throws Exception {
		fundAccount(FUNDED_ACCOUNT, "500000");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","amount":100000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.size").isNumber());
	}

	@Test
	@DisplayName("Una compra por importe insuficiente para una accion queda REJECTED")
	void rejectsBuyWithAmountTooSmall() throws Exception {
		fundAccount(FUNDED_ACCOUNT, "50000");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","amount":1.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());
	}

	@Test
	@DisplayName("Rechaza con 400 el envio de size y amount a la vez")
	void rejectsBothSizeAndAmount() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10,"amount":1000}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 el envio de size y amount ninguno")
	void rejectsNeitherSizeNorAmount() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET"}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una cantidad fraccionaria")
	void rejectsFractionalSize() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10.5}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una orden a mercado con precio")
	void rejectsMarketOrderWithPrice() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10,"price":25000}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una orden limite sin precio")
	void rejectsLimitOrderWithoutPrice() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":10}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 la ausencia de la cabecera de cuenta")
	void rejectsMissingAccountHeader() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 la ausencia de la cabecera de idempotencia")
	void rejectsMissingIdempotencyHeader() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 404 una cuenta inexistente")
	void rejectsUnknownAccount() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", "99999")
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Rechaza con 404 un instrumento inexistente")
	void rejectsUnknownInstrument() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", FUNDED_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"NOPE","side":"BUY","type":"MARKET","size":10}
						"""))
				.andExpect(status().isNotFound());
	}

	/**
	 * Ingresa efectivo en la cuenta para habilitar las operaciones de compra.
	 *
	 * @param accountNumber numero de cuenta
	 * @param amount importe a ingresar
	 */
	private void fundAccount(String accountNumber, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", accountNumber)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":%s}
						""".formatted(amount)))
				.andExpect(status().isCreated());
	}

	private String uniqueKey() {
		return UUID.randomUUID().toString();
	}

}
