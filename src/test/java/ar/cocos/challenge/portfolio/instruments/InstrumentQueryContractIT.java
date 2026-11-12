package ar.cocos.challenge.portfolio.instruments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas funcionales del endpoint {@code GET /api/v1/instruments}.
 *
 * <p>Cubren los escenarios 4 a 8 de la seccion 18 de la spec: listado
 * completo, busqueda por ticker, busqueda por nombre, ausencia de duplicados,
 * busqueda sin coincidencias y busqueda vacia.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InstrumentQueryContractIT {

	/**
	 * Cantidad de instrumentos del catalogo de referencia.
	 */
	private static final int CATALOG_SIZE = 66;

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Sin busqueda devuelve el catalogo completo con el instrumento de efectivo incluido")
	void returnsWholeCatalog() throws Exception {
		mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(CATALOG_SIZE))
				.andExpect(jsonPath("$[0].ticker").exists())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].type").exists());
	}

	@Test
	@DisplayName("El instrumento de efectivo aparece exactamente una vez")
	void includesCashInstrumentExactlyOnce() throws Exception {
		String body = mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body.split("\"ticker\":\"ARS\"", -1).length - 1)
				.isEqualTo(1);
	}

	@Test
	@DisplayName("El catalogo viene ordenado por ticker")
	void returnsCatalogOrderedByTicker() throws Exception {
		String body = mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		int firstA = body.indexOf("\"ticker\":\"A");
		int firstB = body.indexOf("\"ticker\":\"B");
		org.assertj.core.api.Assertions.assertThat(firstA).isLessThan(firstB);
	}

	@Test
	@DisplayName("Busca instrumentos por coincidencia parcial de ticker sin distinguir mayusculas")
	void searchesByPartialTickerIgnoringCase() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").queryParam("query", "ypf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
				.andExpect(jsonPath("$[0].ticker").value(org.hamcrest.Matchers.startsWithIgnoringCase("YPF")));
	}

	@Test
	@DisplayName("Busca instrumentos por coincidencia parcial de nombre")
	void searchesByPartialName() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").queryParam("query", "macro"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
				.andExpect(jsonPath("$[*].ticker").value(org.hamcrest.Matchers.hasItem("BMA")));
	}

	@Test
	@DisplayName("No devuelve duplicados cuando un instrumento coincide por ticker y por nombre")
	void doesNotReturnDuplicates() throws Exception {
		String body = mockMvc.perform(get("/api/v1/instruments").queryParam("query", "capex"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body.split("\"ticker\":\"CAPX\"", -1).length - 1)
				.isLessThanOrEqualTo(1);
	}

	@Test
	@DisplayName("Una busqueda sin coincidencias devuelve una lista vacia")
	void returnsEmptyListWhenNoMatches() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").queryParam("query", "zzzzz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("Una busqueda vacia se rechaza con 400")
	void rejectsBlankQuery() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").queryParam("query", ""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Una busqueda con solo espacios se rechaza con 400")
	void rejectsWhitespaceOnlyQuery() throws Exception {
		mockMvc.perform(get("/api/v1/instruments").queryParam("query", "   "))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("El endpoint no exige la cabecera de cuenta")
	void doesNotRequireAccountHeader() throws Exception {
		mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk());
	}

}
