package ar.cocos.challenge.portfolio.orders.infrastructure;

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
 * Pruebas funcionales de los filtros del historial de ordenes.
 *
 * <p>Cubren los escenarios 54 a 57 de la seccion 18 de la spec: filtros por
 * estado, por lado y por ticker, filtros combinados, valores invalidos y el
 * aislamiento entre cuentas.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderHistoryFilterIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	/**
	 * Otra cuenta, usada para verificar el aislamiento.
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
	@DisplayName("Filtra el historial por estado")
	void filtersByStatus() throws Exception {
		transfer("CASH_IN", "50000.00");
		buyMarket("YPFD", "1");
		limitBuy("YPFD", "1", "100.00");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "FILLED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[?(@.status == 'FILLED')]").exists())
				.andExpect(jsonPath("$.content[?(@.status == 'NEW')]").doesNotExist());
	}

	@Test
	@DisplayName("Filtra el historial por lado")
	void filtersBySide() throws Exception {
		transfer("CASH_IN", "50000.00");
		buyMarket("YPFD", "1");
		limitBuy("YPFD", "1", "100.00");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("side", "CASH_IN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].side").value("CASH_IN"));
	}

	@Test
	@DisplayName("Filtra el historial por ticker con coincidencia exacta")
	void filtersByExactTicker() throws Exception {
		transfer("CASH_IN", "50000.00");
		buyMarket("YPFD", "1");
		buyMarket("GGAL", "2");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("ticker", "YPFD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].ticker").value("YPFD"));
	}

	@Test
	@DisplayName("Combina los filtros de estado, lado e instrumento")
	void combinesFilters() throws Exception {
		transfer("CASH_IN", "50000.00");
		buyMarket("YPFD", "1");
		limitBuy("YPFD", "1", "100.00");
		buyMarket("GGAL", "2");

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "NEW")
				.param("side", "BUY")
				.param("ticker", "YPFD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status").value("NEW"))
				.andExpect(jsonPath("$.content[0].side").value("BUY"))
				.andExpect(jsonPath("$.content[0].ticker").value("YPFD"));
	}

	@Test
	@DisplayName("Un estado invalido se rechaza con 400")
	void rejectsInvalidStatus() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "INVALID"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Un lado invalido se rechaza con 400")
	void rejectsInvalidSide() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("side", "INVALID"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Los filtros nunca devuelven ordenes de otra cuenta")
	void filtersNeverReturnOtherAccountOrders() throws Exception {
		transfer(ACCOUNT, "CASH_IN", "50000.00");
		transfer(OTHER_ACCOUNT, "CASH_IN", "50000.00");
		buyMarket(OTHER_ACCOUNT, "YPFD", "1");

		// El filtro de ticker coincide con una orden de la otra cuenta, pero
		// la cuenta consultada no la tiene.
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("ticker", "YPFD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("status", "FILLED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	private void transfer(String side, String amount) throws Exception {
		transfer(ACCOUNT, side, amount);
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

	private void buyMarket(String ticker, String size) throws Exception {
		buyMarket(ACCOUNT, ticker, size);
	}

	private void buyMarket(String account, String ticker, String size) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"BUY","type":"MARKET","size":%s}
						""".formatted(ticker, size)))
				.andExpect(status().isCreated());
	}

	private void limitBuy(String ticker, String size, String price) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"BUY","type":"LIMIT","size":%s,"price":%s}
						""".formatted(ticker, size, price)))
				.andExpect(status().isCreated());
	}

}
