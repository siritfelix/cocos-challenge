package ar.cocos.challenge.portfolio.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.MissingAccountHeaderException;
import ar.cocos.challenge.portfolio.users.application.AccountResolver;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica la resolucion de cuentas contra la base de datos real, con los
 * datos de referencia precargados por Liquibase.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountResolutionIT {

	@Autowired
	private AccountResolver accountResolver;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	@DisplayName("Resuelve una cuenta existente por su numero de cuenta")
	void resolvesExistingAccount() {
		Account account = accountResolver.resolve("10001");

		assertThat(account.id()).isNotNull();
		assertThat(account.accountNumber()).isEqualTo("10001");
		assertThat(account.email()).isEqualTo("emiliano@test.com");
	}

	@Test
	@DisplayName("Rechaza con 404 una cuenta inexistente")
	void rejectsUnknownAccount() {
		assertThatThrownBy(() -> accountResolver.resolve("99999"))
				.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	@DisplayName("Rechaza con 400 la cabecera de cuenta ausente")
	void rejectsBlankAccountNumber() {
		assertThatThrownBy(() -> accountResolver.resolve(null))
				.isInstanceOf(MissingAccountHeaderException.class);

		assertThatThrownBy(() -> accountResolver.resolve("  "))
				.isInstanceOf(MissingAccountHeaderException.class);
	}

	@Test
	@DisplayName("El bloqueo pesimista devuelve la misma cuenta")
	@Transactional
	void findsAccountForUpdate() {
		Optional<Account> account = accountRepository.findByAccountNumberForUpdate("10002");

		assertThat(account).isPresent();
		assertThat(account.get().accountNumber()).isEqualTo("10002");
	}

	@Test
	@DisplayName("El bloqueo pesimista no encuentra cuentas inexistentes")
	@Transactional
	void doesNotFindUnknownAccountForUpdate() {
		Optional<Account> account = accountRepository.findByAccountNumberForUpdate("99999");

		assertThat(account).isEmpty();
	}

}
