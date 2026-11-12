package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Pruebas funcionales de las transferencias de efectivo.
 *
 * <p>Cubren los escenarios 22 a 25 de la seccion 18 de la spec: ingreso de
 * efectivo, retiro suficiente, retiro insuficiente y las restricciones de
 * forma del request de una transferencia.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CashTransferContractIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	/**
	 * Cuenta sin recursos, usada para verificar el rechazo de un retiro.
	 */
	private static final String EMPTY_ACCOUNT = "10004";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Un ingreso de efectivo queda FILLED con el importe en size y sin precio")
	void createsFilledCashIn() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":10000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.ticker").value("ARS"))
				.andExpect(jsonPath("$.side").value("CASH_IN"))
				.andExpect(jsonPath("$.type").value("MARKET"))
				.andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.size").value(10000.00))
				.andExpect(jsonPath("$.price").doesNotExist())
				.andExpect(jsonPath("$.rejectionReason").doesNotExist());
	}

	@Test
	@DisplayName("Un retiro de efectivo con saldo suficiente queda FILLED")
	void createsFilledCashOut() throws Exception {
		fundAccount(ACCOUNT, "50000.00");

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":25000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.side").value("CASH_OUT"))
				.andExpect(jsonPath("$.status").value("FILLED"))
				.andExpect(jsonPath("$.size").value(25000.00))
				.andExpect(jsonPath("$.price").doesNotExist());
	}

	@Test
	@DisplayName("Un retiro de efectivo sin saldo suficiente queda REJECTED con motivo")
	void rejectsCashOutWithoutCash() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", EMPTY_ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":1000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());
	}

	@Test
	@DisplayName("Rechaza con 400 una transferencia expresada con importe")
	void rejectsTransferWithAmount() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","amount":1000.00}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una transferencia con precio")
	void rejectsTransferWithPrice() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":1000.00,"price":1.00}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una transferencia de tipo LIMIT")
	void rejectsLimitTransfer() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"LIMIT","size":1000.00}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una transferencia sobre un instrumento que no es el de efectivo")
	void rejectsTransferOnNonCashInstrument() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"CASH_IN","type":"MARKET","size":1000.00}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Rechaza con 400 una transferencia con importe cero")
	void rejectsTransferWithZeroAmount() throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":0}
						"""))
				.andExpect(status().isBadRequest());
	}

	/**
	 * Ingresa efectivo en la cuenta.
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
