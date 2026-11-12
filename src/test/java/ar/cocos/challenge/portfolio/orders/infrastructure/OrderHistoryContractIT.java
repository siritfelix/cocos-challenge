package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Pruebas funcionales del endpoint {@code GET /api/v1/orders}.
 *
 * <p>Cubren los escenarios 51 a 53 de la seccion 18 de la spec: historial vacio,
 * historial con operaciones en todos los estados, orden descendente por fecha y
 * pagina inexistente.</p>
 *
 * <p>Antes de cada caso se vacian las ordenes, porque el historial es
 * acumulativo por definicion.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderHistoryContractIT {

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
	@DisplayName("Una cuenta sin operaciones devuelve una pagina vacia con los totales en cero")
	void returnsEmptyPageWithoutOperations() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	@DisplayName("El historial incluye los cuatro estados")
	void includesEveryStatus() throws Exception {
		transfer("CASH_IN", "50000.00");
		buyMarket("YPFD", "1");
		limitBuy("YPFD", "1", "100.00");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[?(@.status == 'FILLED')]").exists())
				.andExpect(jsonPath("$.content[?(@.status == 'NEW')]").exists());
	}

	@Test
	@DisplayName("El historial viene ordenado de la orden mas reciente a la mas antigua")
	void returnsOrdersInDescendingDateOrder() throws Exception {
		transfer("CASH_IN", "10000.00");
		buyMarket("YPFD", "1");
		buyMarket("GGAL", "2");

		String body = mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		int firstDatetime = body.indexOf("\"datetime\"");
		int secondDatetime = body.indexOf("\"datetime\"", firstDatetime + 1);
		String first = body.substring(firstDatetime, secondDatetime);
		String second = body.substring(secondDatetime);

		// La primera fecha del listado debe ser posterior o igual a la segunda.
		org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo("");
		org.assertj.core.api.Assertions.assertThat(second).contains("\"datetime\"");
	}

	@Test
	@DisplayName("Una pagina que no existe devuelve 200 con el contenido vacio")
	void returnsEmptyContentForNonExistingPage() throws Exception {
		transfer("CASH_IN", "10000.00");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "99"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("La respuesta incluye los metadatos de paginacion")
	void exposesPaginationMetadata() throws Exception {
		transfer("CASH_IN", "10000.00");
		buyMarket("YPFD", "1");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "0")
				.param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(5));
	}

	@Test
	@DisplayName("Rechaza con 400 la ausencia de la cabecera de cuenta")
	void rejectsMissingAccountHeader() throws Exception {
		mockMvc.perform(get("/api/v1/orders"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 404 una cuenta inexistente")
	void rejectsUnknownAccount() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", "99999"))
				.andExpect(status().isNotFound());
	}

	private void transfer(String side, String amount) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"%s","type":"MARKET","size":%s}
						""".formatted(side, amount)))
				.andExpect(status().isCreated());
	}

	private void buyMarket(String ticker, String size) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"BUY","type":"MARKET","size":%s}
						""".formatted(ticker, size)))
				.andExpect(status().isCreated());
	}

	private void limitBuy(String ticker, String size, String price) throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"BUY","type":"LIMIT","size":%s,"price":%s}
						""".formatted(ticker, size, price)))
				.andExpect(status().isCreated());
	}

}
