package ar.cocos.challenge.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
/**
 * Verifica que el esquema definido por Liquibase quedo aplicado en la base de
 * datos real del contenedor: las cinco tablas del modelo, sus constraints y los
 * datos de referencia precargados.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsTheFiveModelTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables "
						+ "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
						+ "AND table_name NOT LIKE 'databasechangelog%' "
						+ "ORDER BY table_name",
				String.class);

		assertThat(tables)
				.containsExactly("idempotency_keys", "instruments", "marketdata", "orders", "users");
	}

	@Test
	void createsTheUniqueConstraintsRequiredByTheModel() {
		assertThat(countConstraint("uk_idempotency_keys_user_key")).isEqualTo(1);
		assertThat(countConstraint("uk_idempotency_keys_order_id")).isEqualTo(1);
		assertThat(countConstraint("uk_users_account_number")).isEqualTo(1);
		assertThat(countConstraint("uk_users_email")).isEqualTo(1);
		assertThat(countConstraint("uk_instruments_ticker")).isEqualTo(1);
		assertThat(countConstraint("uk_marketdata_instrument_datetime")).isEqualTo(1);
	}

	@Test
	void createsTheForeignKeysRequiredByTheModel() {
		assertThat(countConstraint("fk_orders_user")).isEqualTo(1);
		assertThat(countConstraint("fk_orders_instrument")).isEqualTo(1);
		assertThat(countConstraint("fk_marketdata_instrument")).isEqualTo(1);
		assertThat(countConstraint("fk_idempotency_keys_user")).isEqualTo(1);
		assertThat(countConstraint("fk_idempotency_keys_order")).isEqualTo(1);
	}

	@Test
	void createsTheCheckConstraintsOfTheDomain() {
		assertThat(countConstraint("ck_instruments_type")).isEqualTo(1);
		assertThat(countConstraint("ck_orders_side")).isEqualTo(1);
		assertThat(countConstraint("ck_orders_type")).isEqualTo(1);
		assertThat(countConstraint("ck_orders_status")).isEqualTo(1);
		assertThat(countConstraint("ck_orders_size_positive")).isEqualTo(1);
		assertThat(countConstraint("ck_orders_price_positive")).isEqualTo(1);
		assertThat(countConstraint("ck_marketdata_high_low")).isEqualTo(1);
		assertThat(countConstraint("ck_marketdata_values_positive")).isEqualTo(1);
	}

	@Test
	void createsThePerformanceIndexes() {
		Integer indexCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname IN ("
						+ "'idx_orders_user_datetime', "
						+ "'idx_orders_user_status_datetime', "
						+ "'idx_orders_instrument_status', "
						+ "'idx_orders_user_instrument_status_datetime_id', "
						+ "'idx_marketdata_instrument_datetime')",
				Integer.class);

		assertThat(indexCount).isEqualTo(5);
	}

	@Test
	void loadsTheReferenceData() {
		Integer users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		Integer instruments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instruments", Integer.class);
		Integer quotes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM marketdata", Integer.class);

		assertThat(users).isEqualTo(4);
		assertThat(instruments).isEqualTo(66);
		assertThat(quotes).isEqualTo(126);
	}

	@Test
	void loadsTheCashInstrumentWithTheNameDefinedByTheSpec() {
		String name = jdbcTemplate.queryForObject(
				"SELECT name FROM instruments WHERE ticker = 'ARS'", String.class);

		assertThat(name).isEqualTo("Peso argentino");
	}

	@Test
	void loadsTheReferenceAccountsIdentifiedByAccountNumber() {
		List<String> accounts = jdbcTemplate.queryForList(
				"SELECT account_number FROM users ORDER BY account_number", String.class);

		assertThat(accounts).containsExactly("10001", "10002", "10003", "10004");
	}

	@Test
	void loadsQuotesWithPreviousCloseDifferentFromClose() {
		// Sin esta propiedad no se puede verificar el rendimiento diario
		// (spec FR-074).
		Integer distinctQuotes = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM marketdata WHERE close <> previous_close", Integer.class);

		assertThat(distinctQuotes).isPositive();
	}

	private int countConstraint(String constraintName) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = ?",
				Integer.class,
				constraintName);
		return count == null ? 0 : count;
	}

}
