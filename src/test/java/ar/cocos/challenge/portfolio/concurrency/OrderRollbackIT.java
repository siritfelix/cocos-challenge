package ar.cocos.challenge.portfolio.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba de atomicidad de la creacion de ordenes.
 *
 * <p>Una falla ocurrida DESPUES de insertar la orden (simulada en la
 * insercion de la clave de idempotencia) debe dejar sin persistir tanto la
 * orden como su clave: la transaccion se revierte completa, de modo que no
 * quedan ordenes huerfanas ni claves que apunten a ordenes inexistentes
 * (spec FR-060).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrderRollbackIT {

	/**
	 * Cuenta de pruebas.
	 */
	private static final String ACCOUNT = "10001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Espia del repositorio de idempotencia: permite provocar una falla exactamente
	 * despues de que la orden fue insertada.
	 */
	@MockitoSpyBean
	private IdempotencyRepository idempotencyRepository;

	@BeforeEach
	void resetOrders() {
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM orders");
	}

	@Test
	@DisplayName("Una falla tras insertar la orden revierte orden y clave de idempotencia")
	void failureAfterOrderInsertRollsBackBoth() throws Exception {
		// Se ingresa efectivo con el espia pasivo: la transferencia confirma
		// normalmente y deja 1 orden y 1 clave.
		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":10000.00}
						"""))
				.andExpect(status().isCreated());

		Long ordersAfterTransfer = jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
		Long keysAfterTransfer = jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class);
		assertThat(ordersAfterTransfer).isEqualTo(1L);
		assertThat(keysAfterTransfer).isEqualTo(1L);

		// A partir de aqui, la insercion de la clave de idempotencia falla,
		// DESPUES de que la orden ya fue insertada en la misma transaccion.
		doThrow(new IllegalStateException("simulated failure after order insert"))
				.when(idempotencyRepository).save(any());

		mockMvc.perform(post("/api/v1/orders")
				.header("X-AccountNumber", ACCOUNT)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}
						"""))
				.andExpect(status().isInternalServerError());

		Long ordersAfterFailure = jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
		Long keysAfterFailure = jdbcTemplate.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class);
		assertThat(ordersAfterFailure)
				.as("la orden no queda persistida: el rollback deshace su insert")
				.isEqualTo(1L);
		assertThat(keysAfterFailure)
				.as("la clave de idempotencia no queda persistida: el rollback deshace su insert")
				.isEqualTo(1L);
	}

}
