package ar.cocos.challenge.portfolio.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
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
 * Pruebas funcionales del efecto de las transferencias sobre los recursos de la
 * cuenta.
 *
 * <p>Cubren los escenarios 24 y 25 de la seccion 18 de la spec: el retiro
 * consume el efectivo disponible y un retiro que excede el efectivo total pero
 * no el disponible queda rechazado, porque el efectivo reservado por compras
 * limite pendientes no puede retirarse (spec FR-034, FR-037, FR-051).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CashTransferResourceIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10002";

	/**
	 * Tolerancia de comparacion de importes, en centimos.
	 */
	private static final org.assertj.core.data.Offset<BigDecimal> TOLERANCE =
			org.assertj.core.data.Offset.offset(new BigDecimal("0.01"));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Un ingreso de efectivo aumenta el efectivo de la cuenta en el importe")
	void cashInIncreasesBalance() throws Exception {
		BigDecimal before = availableCash();

		transfer("CASH_IN", "10000.00");

		assertThat(availableCash()).isCloseTo(before.add(new BigDecimal("10000.00")), TOLERANCE);
	}

	@Test
	@DisplayName("Un retiro de efectivo disminuye el efectivo de la cuenta en el importe")
	void cashOutDecreasesBalance() throws Exception {
		transfer("CASH_IN", "50000.00");
		BigDecimal before = availableCash();

		transfer("CASH_OUT", "20000.00");

		assertThat(availableCash()).isCloseTo(before.subtract(new BigDecimal("20000.00")), TOLERANCE);
	}

	@Test
	@DisplayName("Un retiro que excede el efectivo disponible queda rechazado y no altera el saldo")
	void cashOutBeyondAvailableIsRejected() throws Exception {
		transfer("CASH_IN", "10000.00");
		BigDecimal before = availableCash();

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":50000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.rejectionReason").isNotEmpty());

		assertThat(availableCash()).isCloseTo(before, TOLERANCE);
	}

	@Test
	@DisplayName("El efectivo reservado por compras limite pendientes no puede retirarse")
	void reservedCashCannotBeWithdrawn() throws Exception {
		// Se ingiere efectivo y se reserva con una compra limite pendiente.
		transfer("CASH_IN", "100000.00");
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":90000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("NEW"));

		// El efectivo total es 100.000, pero 90.000 estan reservados: solo
		// 10.000 pueden retirarse.
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":50000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"));
	}

	/**
	 * Calcula el efectivo disponible de la cuenta de pruebas.
	 *
	 * <p>Reproduce la formula de la spec: ingresos y ventas menos retiros y
	 * compras ejecutadas, menos las reservas de compras limite pendientes.</p>
	 *
	 * @return el efectivo disponible
	 */
	private BigDecimal availableCash() {
		BigDecimal filled = jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(
				    CASE
				        WHEN side = 'CASH_IN' THEN size
				        WHEN side = 'CASH_OUT' THEN -size
				        WHEN side = 'BUY' THEN -(size * price)
				        WHEN side = 'SELL' THEN (size * price)
				        ELSE 0
				    END), 0)
				FROM orders
				WHERE user_id = (SELECT id FROM users WHERE account_number = ?)
				  AND status = 'FILLED'
				""", BigDecimal.class, ACCOUNT);
		BigDecimal reserved = jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(size * price), 0)
				FROM orders
				WHERE user_id = (SELECT id FROM users WHERE account_number = ?)
				  AND status = 'NEW' AND side = 'BUY'
				""", BigDecimal.class, ACCOUNT);
		return filled.subtract(reserved);
	}

	private void transfer(String side, String amount) throws Exception {
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", uniqueKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"%s","type":"MARKET","size":%s}
						""".formatted(side, amount)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FILLED"));
	}

	private String uniqueKey() {
		return UUID.randomUUID().toString();
	}

}
