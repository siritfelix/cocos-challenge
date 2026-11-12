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
 * Pruebas funcionales de la paginacion del historial de ordenes.
 *
 * <p>Cubren el escenario 58 de la seccion 18 de la spec y las validaciones de
 * FR-082: pagina negativa, tamano fuera de rango y valores no numericos.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderHistoryPaginationIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	/**
	 * Tamano de pagina minimo admitido.
	 */
	private static final int MIN_PAGE_SIZE = 1;

	/**
	 * Tamano de pagina maximo admitido.
	 */
	private static final int MAX_PAGE_SIZE = 100;

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
	@DisplayName("Una pagina negativa se rechaza con 400")
	void rejectsNegativePage() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "-1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Un tamano de pagina cero se rechaza con 400")
	void rejectsZeroPageSize() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("size", "0"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Un tamano de pagina mayor a 100 se rechaza con 400")
	void rejectsTooLargePageSize() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("size", String.valueOf(MAX_PAGE_SIZE + 1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Un tamano de pagina no numerico se rechaza con 400")
	void rejectsNonNumericPageSize() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("size", "abc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Una pagina no numerica se rechaza con 400")
	void rejectsNonNumericPage() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "abc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Los valores por defecto son pagina 0 y tamano 20")
	void appliesDefaultPagination() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	@DisplayName("Los limites admitidos de tamano de pagina son aceptados")
	void acceptsValidPageSizes() throws Exception {
		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("size", String.valueOf(MIN_PAGE_SIZE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(MIN_PAGE_SIZE));

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("size", String.valueOf(MAX_PAGE_SIZE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(MAX_PAGE_SIZE));
	}

	@Test
	@DisplayName("La paginacion divide el historial en paginas disjuntas")
	void splitsHistoryInDisjointPages() throws Exception {
		for (int index = 0; index < 5; index++) {
			buyMarket("YPFD", "1");
		}

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "0")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3));

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "1")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2));

		mockMvc.perform(get("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.param("page", "2")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));
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

}
