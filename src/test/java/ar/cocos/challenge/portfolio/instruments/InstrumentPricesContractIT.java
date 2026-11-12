package ar.cocos.challenge.portfolio.instruments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas funcionales de los precios en el catalogo de instrumentos.
 *
 * <p>Cubren la historia de usuario 1 de la spec 002: el listado completo
 * incluye el ultimo precio disponible y el cierre anterior de cada instrumento
 * obtenidos de su cotizacion mas reciente; el instrumento sin cotizacion
 * aparece con sus datos basicos y sin campos de precio.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InstrumentPricesContractIT {

	/**
	 * Ultimo cierre de YPFD en el seed de cotizaciones (fila con mayor datetime).
	 */
	private static final String YPFD_LATEST_CLOSE = "7837.50";

	/**
	 * Cierre anterior de esa misma cotizacion del seed.
	 */
	private static final String YPFD_LATEST_PREVIOUS_CLOSE = "7975.05";

	/**
	 * Tickers del seed sin ninguna cotizacion registrada: el efectivo y dos
	 * acciones sin precios de referencia.
	 */
	private static final java.util.List<String> TICKERS_WITHOUT_QUOTES =
			java.util.List.of("ARS", "IRCP", "PGR");

	@Autowired
	private MockMvc mockMvc;

	/**
	 * Parser de las respuestas del catalogo.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	@DisplayName("El listado completo incluye el ultimo precio y el cierre anterior de la cotizacion mas reciente")
	void fullCatalogIncludesPrices() throws Exception {
		JsonNode ypfd = instrumentByTicker("YPFD");

		assertThat(ypfd.hasNonNull("price")).isTrue();
		assertThat(ypfd.path("price").decimalValue())
				.as("el price del catalogo es el cierre de la cotizacion mas reciente del seed")
				.isEqualByComparingTo(YPFD_LATEST_CLOSE);
		assertThat(ypfd.hasNonNull("previousClose")).isTrue();
		assertThat(ypfd.path("previousClose").decimalValue())
				.as("el previousClose del catalogo es el cierre anterior de esa misma cotizacion")
				.isEqualByComparingTo(YPFD_LATEST_PREVIOUS_CLOSE);
	}

	@Test
	@DisplayName("El instrumento sin cotizacion aparece con sus datos basicos y sin campos de precio")
	void instrumentWithoutQuoteHasNoPriceFields() throws Exception {
		JsonNode ars = instrumentByTicker("ARS");

		assertThat(ars.has("id")).isTrue();
		assertThat(ars.path("name").asText()).isEqualTo("Peso argentino");
		assertThat(ars.path("type").asText()).isEqualTo("MONEDA");
		assertThat(ars.has("price"))
				.as("un instrumento sin cotizacion no expone price")
				.isFalse();
		assertThat(ars.has("previousClose"))
				.as("un instrumento sin cotizacion no expone previousClose")
				.isFalse();
	}

	@Test
	@DisplayName("Todos los instrumentos con cotizacion del catalogo informan su precio")
	void everyQuotedInstrumentHasPrices() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode catalog = MAPPER.readTree(result.getResponse().getContentAsString());

		int withQuote = 0;
		for (JsonNode instrument : catalog) {
			if (TICKERS_WITHOUT_QUOTES.contains(instrument.path("ticker").asText())) {
				assertThat(instrument.has("price")).isFalse();
				assertThat(instrument.has("previousClose")).isFalse();
			} else {
				assertThat(instrument.hasNonNull("price"))
						.as("el instrumento %s del seed tiene cotizacion y debe exponer price",
								instrument.path("ticker").asText())
						.isTrue();
				assertThat(instrument.hasNonNull("previousClose"))
						.as("el instrumento %s del seed tiene cotizacion y debe exponer previousClose",
								instrument.path("ticker").asText())
						.isTrue();
				withQuote++;
			}
		}
		assertThat(withQuote)
				.as("los demas instrumentos del catalogo de referencia tienen cotizaciones")
				.isEqualTo(63);
	}

	@Test
	@DisplayName("La consulta con precios responde 200 y conserva la estructura basica")
	void catalogStillRespondsWithBasicStructure() throws Exception {
		mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(66))
				.andExpect(jsonPath("$[0].ticker").exists())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].type").exists())
				.andExpect(jsonPath("$[0].id").exists());
	}

	@Test
	@DisplayName("La busqueda por ticker case-insensitive devuelve el mismo instrumento con sus precios")
	void searchByTickerIncludesPrices() throws Exception {
		JsonNode result = searchInstrument("ypf");

		assertThat(result.path("ticker").asText()).isEqualTo("YPFD");
		assertThat(result.hasNonNull("price")).isTrue();
		assertThat(result.path("price").decimalValue()).isEqualByComparingTo(YPFD_LATEST_CLOSE);
		assertThat(result.path("previousClose").decimalValue()).isEqualByComparingTo(YPFD_LATEST_PREVIOUS_CLOSE);
	}

	@Test
	@DisplayName("La busqueda por nombre tambien enriquece los resultados con precios")
	void searchByNameIncludesPrices() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/instruments").param("query", "macro"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode search = MAPPER.readTree(result.getResponse().getContentAsString());

		assertThat(search).isNotEmpty();
		assertThat(search.get(0).path("ticker").asText()).isEqualTo("BMA");
		for (JsonNode instrument : search) {
			if (!TICKERS_WITHOUT_QUOTES.contains(instrument.path("ticker").asText())) {
				assertThat(instrument.hasNonNull("price"))
						.as("la busqueda debe entregar los mismos precios que el listado completo")
						.isTrue();
			}
		}
	}

	@Test
	@DisplayName("La busqueda sin coincidencias sigue devolviendo una lista vacia")
	void searchWithoutMatchesStillReturnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").param("query", "zzzzz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * @param ticker simbolo buscado
	 * @return el instrumento del catalogo con ese ticker
	 * @throws Exception si la llamada falla
	 */
	private JsonNode instrumentByTicker(String ticker) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode catalog = MAPPER.readTree(result.getResponse().getContentAsString());
		for (JsonNode instrument : catalog) {
			if (ticker.equals(instrument.path("ticker").asText())) {
				return instrument;
			}
		}
		throw new AssertionError("Instrument not found in catalog: " + ticker);
	}

	/**
	 * @param query texto de busqueda
	 * @return el unico instrumento devuelto por la busqueda
	 * @throws Exception si la llamada falla
	 */
	private JsonNode searchInstrument(String query) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/instruments").param("query", query))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode search = MAPPER.readTree(result.getResponse().getContentAsString());
		if (search.size() != 1) {
			throw new AssertionError("Expected exactly one search result for " + query + " but got " + search.size());
		}
		return search.get(0);
	}

}
