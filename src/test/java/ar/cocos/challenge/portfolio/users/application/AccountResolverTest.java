package ar.cocos.challenge.portfolio.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.MissingAccountHeaderException;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitarios de la resolucion de la cuenta a partir de su numero.
 *
 * <p>Cubren los escenarios 1, 2 y 3 de la seccion 18.1 de la spec: cabecera
 * ausente, cuenta inexistente y cuenta valida.</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountResolverTest {

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountResolver accountResolver;

	@Test
	@DisplayName("Resuelve la cuenta cuando el numero de cuenta existe")
	void resolvesExistingAccount() {
		Account account = new Account(1L, "emiliano@test.com", "10001");
		given(accountRepository.findByAccountNumber("10001")).willReturn(Optional.of(account));

		Account resolved = accountResolver.resolve("10001");

		assertThat(resolved).isEqualTo(account);
	}

	@Test
	@DisplayName("Rechaza con 400 cuando la cabecera de cuenta viene vacia")
	void rejectsBlankAccountNumber() {
		assertThatThrownBy(() -> accountResolver.resolve(null))
				.isInstanceOf(MissingAccountHeaderException.class)
				.hasMessageContaining("X-AccountNumber");

		assertThatThrownBy(() -> accountResolver.resolve("   "))
				.isInstanceOf(MissingAccountHeaderException.class);

		verify(accountRepository, never()).findByAccountNumber(null);
	}

	@Test
	@DisplayName("Rechaza con 404 cuando la cuenta no existe")
	void rejectsUnknownAccount() {
		given(accountRepository.findByAccountNumber("99999")).willReturn(Optional.empty());

		assertThatThrownBy(() -> accountResolver.resolve("99999"))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessageContaining("99999");
	}

}
