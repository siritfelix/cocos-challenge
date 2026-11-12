package ar.cocos.challenge.portfolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
 * Pruebas funcionales del endpoint {@code GET /api/v1/portfolio}.
 *
 * <p>Cubren los escenarios 42 a 50 de la seccion 18 de la spec: consulta sin
 * operaciones, con efectivo, con posiciones, con ordenes pendientes, y la
 * verificacion de los tres montos de efectivo, las cantidades y los
 * rendimientos.</p>
 *
 * <p>Los tests comparten la base del contenedor y las ordenes son
 * acumulativas por definicion, asi que antes de cada caso se vacian las tablas
 * de ordenes e idempotencia. Asi cada caso arranca de un estado conocido y no
 * depende del orden de ejecucion.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioContractIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Deja la cuenta de pruebas sin operaciones: el portfolio se deriva
	 * integramente de las ordenes registradas (spec D-047).
	 */
	@BeforeEach
	void resetAccount() {
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	@DisplayName("Una cuenta sin operaciones devuelve ceros y lista de posiciones vacia")
	void returnsEmptyPortfolioForAccountWithoutOperations() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCash").value(0.00))
				.andExpect(jsonPath("$.availableCash").value(0.00))
				.andExpect(jsonPath("$.reservedCash").value(0.00))
				.andExpect(jsonPath("$.totalValue").value(0.00))
				.andExpect(jsonPath("$.positions").isEmpty());
	}

	@Test
	@DisplayName("Rechaza con 400 la ausencia de la cabecera de cuenta")
	void rejectsMissingAccountHeader() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 404 una cuenta inexistente")
	void rejectsUnknownAccount() throws Exception {
		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", "99999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Una cuenta con solo ingresos muestra el efectivo y ninguna posicion")
	void returnsCashWithoutPositions() throws Exception {
		transfer("CASH_IN", "25000.00");

		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCash").value(25000.00))
				.andExpect(jsonPath("$.availableCash").value(25000.00))
				.andExpect(jsonPath("$.reservedCash").value(0.00))
				.andExpect(jsonPath("$.totalValue").value(25000.00))
				.andExpect(jsonPath("$.positions").isEmpty());
	}

	@Test
	@DisplayName("Una compra ejecutada genera una posicion con valor de mercado y costo promedio")
	void returnsPositionAfterExecutedBuy() throws Exception {
		transfer("CASH_IN", "100000.00");
		buyMarket("YPFD", "2");

		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions[0].ticker").value("YPFD"))
				.andExpect(jsonPath("$.positions[0].totalQuantity").value(2.00))
				.andExpect(jsonPath("$.positions[0].availableQuantity").value(2.00))
				.andExpect(jsonPath("$.positions[0].reservedQuantity").value(0.00))
				.andExpect(jsonPath("$.positions[0].currentPrice").isNumber())
				.andExpect(jsonPath("$.positions[0].marketValue").isNumber())
				.andExpect(jsonPath("$.positions[0].averageCost").isNumber())
				.andExpect(jsonPath("$.positions[0].totalPerformance").isNumber())
				.andExpect(jsonPath("$.positions[0].dailyPerformance").isNumber());
	}

	@Test
	@DisplayName("Una orden pendiente no altera el patrimonio total pero si el efectivo disponible")
	void pendingOrderReservesCashWithoutChangingTotalValue() throws Exception {
		transfer("CASH_IN", "100000.00");
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":50000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));

		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				// El efectivo total no cambia: la orden pendiente solo reserva.
				.andExpect(jsonPath("$.totalCash").value(100000.00))
				.andExpect(jsonPath("$.reservedCash").value(50000.00))
				.andExpect(jsonPath("$.availableCash").value(50000.00))
				// El patrimonio total tampoco cambia.
				.andExpect(jsonPath("$.totalValue").value(100000.00));
	}

	@Test
	@DisplayName("Una venta pendiente reserva acciones sin alterar la cantidad total")
	void pendingSaleReservesShares() throws Exception {
		transfer("CASH_IN", "100000.00");
		buyMarket("YPFD", "5");
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"SELL","type":"LIMIT","size":2,"price":9000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));

		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions[0].totalQuantity").value(5.00))
				.andExpect(jsonPath("$.positions[0].reservedQuantity").value(2.00))
				.andExpect(jsonPath("$.positions[0].availableQuantity").value(3.00));
	}

	@Test
	@DisplayName("Una posicion cerrada no se lista en el portfolio")
	void closedPositionIsNotListed() throws Exception {
		transfer("CASH_IN", "100000.00");
		buyMarket("YPFD", "5");
		sellMarket("YPFD", "5");

		mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions").isEmpty());
	}

	@Test
	@DisplayName("El valor total del portfolio es el efectivo mas el valor de mercado de las posiciones")
	void totalValueIsCashPlusMarketValue() throws Exception {
		transfer("CASH_IN", "60000.00");
		buyMarket("YPFD", "3");

		String body = mockMvc.perform(get("/api/v1/portfolio")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		BigDecimal totalCash = valueOf(body, "totalCash");
		BigDecimal totalValue = valueOf(body, "totalValue");
		BigDecimal marketValue = marketValueOf(body);

		assertThat(totalValue).isEqualByComparingTo(totalCash.add(marketValue));
	}

	/**
	 * Extrae un valor numerico del cuerpo JSON de la respuesta.
	 *
	 * @param body cuerpo de la respuesta
	 * @param field nombre del campo
	 * @return el valor del campo
	 */
	private BigDecimal valueOf(String body, String field) {
		String pattern = "\"" + field + "\":";
		int start = body.indexOf(pattern) + pattern.length();
		int end = body.indexOf(',', start);
		if (end < 0) {
			end = body.indexOf('}', start);
		}
		return new BigDecimal(body.substring(start, end).trim());
	}

	private BigDecimal marketValueOf(String body) {
		String pattern = "\"marketValue\":";
		int start = body.indexOf(pattern) + pattern.length();
		int end = body.indexOf(',', start);
		if (end < 0) {
			end = body.indexOf('}', start);
		}
		return new BigDecimal(body.substring(start, end).trim());
	}

	private void transfer(String side, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"%s","type":"MARKET","size":%s}
						""".formatted(side, amount)))
				.andExpect(status().isCreated());
	}

	private void buyMarket(String ticker, String size) throws Exception {
		trade(ticker, "BUY", size);
	}

	private void sellMarket(String ticker, String size) throws Exception {
		trade(ticker, "SELL", size);
	}

	private void trade(String ticker, String side, String size) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"%s","side":"%s","type":"MARKET","size":%s}
						""".formatted(ticker, side, size)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FILLED"));
	}

	private String uniqueKey() {
		return UUID.randomUUID().toString();
	}

}
