package ar.cocos.challenge.portfolio.concurrency;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente HTTP y coordinador de hilos de las pruebas de concurrencia.
 *
 * <p>Las pruebas de concurrencia ejercitan la API real: el servidor arranca en
 * un puerto aleatorio y las peticiones simultaneas se despachan por HTTP, de
 * modo que cada una corre en su propio hilo de Tomcat con su propia
 * transaccion, igual que en produccion.</p>
 */
final class ConcurrentHttp {

	/**
	 * Respuesta cruda de una llamada HTTP.
	 *
	 * @param status codigo de estado HTTP
	 * @param body cuerpo parseado como JSON, o vacio si no es JSON
	 */
	record Response(int status, JsonNode body) {

		/**
		 * @param field nombre del campo
		 * @return {@code true} si el cuerpo contiene el campo con valor no nulo
		 */
		boolean has(String field) {
			return body != null && body.hasNonNull(field);
		}

		/**
		 * @param field nombre del campo
		 * @return el valor del campo como texto, o {@code null} si esta ausente
		 */
		String text(String field) {
			return body == null ? null : body.path(field).asText(null);
		}
	}

	/**
	 * Parser de los cuerpos de respuesta.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Constructor privado: la clase solo expone metodos estaticos.
	 */
	private ConcurrentHttp() {
	}

	/**
	 * Crea una orden o transferencia por HTTP.
	 *
	 * @param baseUrl raiz del servidor en marcha
	 * @param account cabecera {@code X-AccountNumber}
	 * @param idempotencyKey cabecera {@code Idempotency-Key}
	 * @param json cuerpo de la solicitud
	 * @return la respuesta del servidor
	 * @throws IOException si falla la comunicacion
	 * @throws InterruptedException si el hilo es interrumpido
	 */
	static Response post(String baseUrl, String account, String idempotencyKey, String json)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/v1/orders"))
				.header("X-AccountNumber", account)
				.header("Idempotency-Key", idempotencyKey)
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(30))
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return execute(request);
	}

	/**
	 * Cancela una orden por HTTP.
	 *
	 * @param baseUrl raiz del servidor en marcha
	 * @param account cabecera {@code X-AccountNumber}
	 * @param orderId identificador de la orden
	 * @return la respuesta del servidor
	 * @throws IOException si falla la comunicacion
	 * @throws InterruptedException si el hilo es interrumpido
	 */
	static Response delete(String baseUrl, String account, long orderId)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/api/v1/orders/" + orderId))
				.header("X-AccountNumber", account)
				.timeout(Duration.ofSeconds(30))
				.DELETE()
				.build();
		return execute(request);
	}

	/**
	 * Consulta un recurso por HTTP.
	 *
	 * @param baseUrl raiz del servidor en marcha
	 * @param path ruta relativa, por ejemplo {@code /api/v1/portfolio}
	 * @param account cabecera {@code X-AccountNumber}
	 * @return la respuesta del servidor
	 * @throws IOException si falla la comunicacion
	 * @throws InterruptedException si el hilo es interrumpido
	 */
	static Response get(String baseUrl, String path, String account)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + path))
				.header("X-AccountNumber", account)
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		return execute(request);
	}

	/**
	 * Ejecuta una tarea en varios hilos que arrancan a la vez.
	 *
	 * <p>Un latch retiene a todos los hilos hasta que estan listos, de modo que
	 * las llamadas se solapan lo mas posible y compitan por los mismos
	 * recursos.</p>
	 *
	 * @param <T> tipo de resultado de la tarea
	 * @param threads cantidad de hilos
	 * @param task tarea a ejecutar en cada hilo
	 * @return los resultados, uno por hilo
	 * @throws Exception si la tarea falla o excede el tiempo de espera
	 */
	static <T> List<T> runSimultaneously(int threads, Callable<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					startGate.await();
					return task.call();
				}));
			}
			startGate.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Ejecuta una solicitud y captura cualquier estado HTTP.
	 *
	 * <p>A diferencia de otros clientes, {@code HttpClient} no lanza por un
	 * {@code 4xx} o {@code 5xx}: la respuesta se devuelve tal cual para que la
	 * prueba la verifique.</p>
	 *
	 * @param request solicitud a enviar
	 * @return la respuesta del servidor
	 * @throws IOException si falla la comunicacion
	 * @throws InterruptedException si el hilo es interrumpido
	 */
	private static Response execute(HttpRequest request) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode body = parse(httpResponse.body());
		return new Response(httpResponse.statusCode(), body);
	}

	/**
	 * @param raw cuerpo crudo de la respuesta
	 * @return el cuerpo parseado, o {@code null} si no es JSON valido
	 */
	private static JsonNode parse(String raw) {
		try {
			return MAPPER.readTree(raw);
		} catch (IOException exception) {
			return null;
		}
	}

}
