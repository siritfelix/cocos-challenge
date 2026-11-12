package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas funcionales de la idempotencia en la creacion de ordenes.
 *
 * <p>Cubren los escenarios 33 a 36 de la seccion 18 de la spec: clave nueva,
 * repeticion con el mismo contenido, reutilizacion con contenido distinto y
 * peticiones concurrentes con la misma clave.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIT {

	private static final String ACCOUNT = "10001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Repetir la misma solicitud con la misma clave devuelve la misma orden")
	void replaysOriginalOrder() throws Exception {
		String key = UUID.randomUUID().toString();
		String body = """
				{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}
				""";

		String firstId = createOrder(key, body);
		String secondId = createOrder(key, body);

		org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
	}

	@Test
	@DisplayName("Reutilizar la misma clave con contenido distinto responde 409")
	void rejectsSameKeyWithDifferentContent() throws Exception {
		String key = UUID.randomUUID().toString();
		createOrder(key, """
				{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}
				""");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":2,"price":100.00}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("Dos peticiones concurrentes con la misma clave producen una sola orden")
	void concurrentRequestsWithSameKeyProduceOneOrder() throws Exception {
		String key = UUID.randomUUID().toString();
		String body = """
				{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}
				""";

		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.CountDownLatch startSignal = new java.util.concurrent.CountDownLatch(1);
			java.util.List<java.util.concurrent.Callable<String>> tasks = java.util.List.of(
					() -> {
						startSignal.await(30, java.util.concurrent.TimeUnit.SECONDS);
						return createOrder(key, body);
					},
					() -> {
						startSignal.await(30, java.util.concurrent.TimeUnit.SECONDS);
						return createOrder(key, body);
					});

			var futures = executor.invokeAll(tasks);
			startSignal.countDown();

			String firstId = futures.get(0).get(60, java.util.concurrent.TimeUnit.SECONDS);
			String secondId = futures.get(1).get(60, java.util.concurrent.TimeUnit.SECONDS);

			org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
		} finally {
			executor.shutdownNow();
		}
	}

	private String createOrder(String key, String body) throws Exception {
		String response = mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		org.assertj.core.api.Assertions.assertThat(response).contains("\"id\"");
		return extractId(response);
	}

	private String extractId(String response) {
		int start = response.indexOf("\"id\":") + 5;
		int end = response.indexOf(',', start);
		if (end < 0) {
			end = response.indexOf('}', start);
		}
		return response.substring(start, end).trim();
	}

}
