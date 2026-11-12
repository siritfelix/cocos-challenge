package ar.cocos.challenge.portfolio.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
 * Prueba de documentacion de la API.
 *
 * <p>El documento OpenAPI publicado en runtime debe declarar los 4 paths de la
 * API, las cabeceras obligatorias de cada operacion y la estructura de error
 * unificada; ademas, el conjunto de paths debe coincidir con el contrato
 * versionado de la especificacion (Principio VI).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

	/**
	 * Ruta del documento OpenAPI generado en runtime.
	 */
	private static final String API_DOCS_PATH = "/v3/api-docs";

	/**
	 * Rutas que la API debe exponer segun el contrato.
	 */
	private static final List<String> EXPECTED_PATHS = List.of(
			"/api/v1/instruments", "/api/v1/orders", "/api/v1/orders/{orderId}", "/api/v1/portfolio");

	/**
	 * Cabecera de cuenta obligatoria en todos los endpoints que operan sobre una cuenta.
	 */
	private static final String ACCOUNT_HEADER = "X-AccountNumber";

	/**
	 * Ruta del contrato OpenAPI versionado con la especificacion.
	 */
	private static final Path CONTRACT_YAML = Path.of(
			"specs", "001-investment-portfolio-api", "contracts", "openapi.yaml");

	@Autowired
	private MockMvc mockMvc;

	/**
	 * Parser de los documentos OpenAPI en runtime y del contrato.
	 */
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	@DisplayName("El OpenAPI en runtime declara los 4 paths de la API")
	void publishesAllPaths() throws Exception {
		JsonNode paths = apiDocs().path("paths");
		assertThat(paths.isObject()).isTrue();
		for (String expected : EXPECTED_PATHS) {
			assertThat(paths.has(expected))
					.as("el path %s debe estar publicado en el OpenAPI", expected)
					.isTrue();
		}
	}

	@Test
	@DisplayName("El OpenAPI en runtime declara la cabecera X-AccountNumber como obligatoria")
	void publishesAccountHeaderAsRequired() throws Exception {
		JsonNode paths = apiDocs().path("paths");
		// Los endpoints que operan sobre una cuenta son los de ordenes y
		// portfolio; el catalogo de instrumentos no identifica cuenta.
		assertThat(hasRequiredHeader(paths.path("/api/v1/orders").path("post").path("parameters"), ACCOUNT_HEADER))
				.as("el POST de ordenes debe declarar la cabecera %s obligatoria", ACCOUNT_HEADER)
				.isTrue();
		assertThat(hasRequiredHeader(paths.path("/api/v1/orders").path("get").path("parameters"), ACCOUNT_HEADER))
				.as("el GET de ordenes debe declarar la cabecera %s obligatoria", ACCOUNT_HEADER)
				.isTrue();
		assertThat(hasRequiredHeader(
				paths.path("/api/v1/orders/{orderId}").path("delete").path("parameters"), ACCOUNT_HEADER))
				.as("el DELETE de ordenes debe declarar la cabecera %s obligatoria", ACCOUNT_HEADER)
				.isTrue();
		assertThat(hasRequiredHeader(paths.path("/api/v1/portfolio").path("get").path("parameters"), ACCOUNT_HEADER))
				.as("el GET de portfolio debe declarar la cabecera %s obligatoria", ACCOUNT_HEADER)
				.isTrue();
	}

	@Test
	@DisplayName("El OpenAPI en runtime declara la estructura de error unificada")
	void publishesUnifiedErrorSchema() throws Exception {
		JsonNode apiDocs = apiDocs();
		JsonNode schemas = apiDocs.path("components").path("schemas");
		assertThat(schemas.has("ErrorResponse"))
				.as("debe existir el esquema ErrorResponse de la estructura de error unificada")
				.isTrue();

		// Los campos del ErrorResponse unificado, segun el contrato.
		JsonNode errorResponse = schemas.path("ErrorResponse").path("properties");
		for (String field : List.of("timestamp", "status", "error", "message", "path")) {
			assertThat(errorResponse.has(field))
					.as("ErrorResponse debe declarar el campo %s", field)
					.isTrue();
		}

		// Las respuestas de error de las operaciones referencian el esquema.
		assertThat(apiDocs.path("paths").path("/api/v1/orders").path("post")
				.path("responses").path("409").path("content").toString())
				.as("la respuesta 409 del POST de ordenes debe referenciar el esquema de error")
				.contains("ErrorResponse");
	}

	@Test
	@DisplayName("El OpenAPI en runtime declara la cabecera Idempotency-Key en la creacion de ordenes")
	void publishesIdempotencyHeader() throws Exception {
		JsonNode parameters = apiDocs().path("paths").path("/api/v1/orders").path("post").path("parameters");
		assertThat(hasRequiredHeader(parameters, "Idempotency-Key"))
				.as("el POST de ordenes debe declarar la cabecera Idempotency-Key obligatoria")
				.isTrue();
	}

	@Test
	@DisplayName("El conjunto de paths del runtime coincide con el contrato versionado")
	void runtimePathsMatchTheContract() throws Exception {
		assertThat(CONTRACT_YAML).as("el contrato OpenAPI de la especificacion debe existir").exists();
		JsonNode runtimePaths = apiDocs().path("paths");
		List<String> runtime = iterableFieldNames(runtimePaths).stream().sorted().toList();

		Pattern topLevelPath = Pattern.compile("^  (/api/v1[^:]*):");
		try (Stream<String> lines = Files.readAllLines(CONTRACT_YAML).stream()) {
			List<String> contract = lines
					.map(topLevelPath::matcher)
					.filter(Matcher::matches)
					.map(matcher -> matcher.group(1))
					.distinct()
					.sorted()
					.toList();

			assertThat(contract)
					.as("el contrato declara los paths de la API")
					.isNotEmpty();
			assertThat(runtime)
					.as("los paths publicados en runtime deben coincidir con el contrato versionado")
					.isEqualTo(contract);
		}
	}

	@Test
	@DisplayName("El schema del catalogo del runtime declara price y previousClose opcionales")
	void publishesOptionalPriceFields() throws Exception {
		JsonNode schemas = apiDocs().path("components").path("schemas");
		JsonNode instrument = schemas.path("InstrumentResponse");
		JsonNode properties = instrument.path("properties");
		assertThat(properties.has("price"))
				.as("el schema del catalogo debe declarar el campo price; schemas publicados: %s; path instruments: %s",
						iterableFieldNames(schemas), apiDocs().path("paths").path("/api/v1/instruments"))
				.isTrue();
		assertThat(properties.has("previousClose"))
				.as("el schema del catalogo debe declarar el campo previousClose")
				.isTrue();
		for (JsonNode required : instrument.path("required")) {
			assertThat(required.asText())
					.as("price y previousClose deben ser opcionales: un instrumento sin cotizacion no los expone")
					.isNotIn("price", "previousClose");
		}
	}

	/**
	 * @return el documento OpenAPI generado en runtime
	 * @throws Exception si la llamada falla
	 */
	private JsonNode apiDocs() throws Exception {
		MvcResult result = mockMvc.perform(get(API_DOCS_PATH))
				.andExpect(status().isOk())
				.andReturn();
		return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
	}

	/**
	 * @param parameters arreglo de parametros de una operacion
	 * @param name nombre de la cabecera buscada
	 * @return {@code true} si declara la cabecera con {@code in: header} y {@code required: true}
	 */
	private boolean hasRequiredHeader(JsonNode parameters, String name) {
		if (!parameters.isArray()) {
			return false;
		}
		for (JsonNode parameter : parameters) {
			boolean isHeader = "header".equals(parameter.path("in").asText());
			boolean isRequired = parameter.path("required").asBoolean(false);
			if (name.equals(parameter.path("name").asText()) && isHeader && isRequired) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param node objeto JSON
	 * @return los nombres de sus campos
	 */
	private List<String> iterableFieldNames(JsonNode node) {
		List<String> names = new java.util.ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}

}
