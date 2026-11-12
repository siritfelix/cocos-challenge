package ar.cocos.challenge.portfolio.users.application;

import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.MissingAccountHeaderException;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve la cuenta a partir del numero de cuenta recibido en la cabecera.
 *
 * <p>Comportamiento (spec FR-002, FR-003):</p>
 * <ul>
 *   <li>Cabecera ausente o vacia: {@code 400 Bad Request}.</li>
 *   <li>Cuenta inexistente: {@code 404 Not Found}.</li>
 *   <li>Cuenta existente: se continúa con el procesamiento.</li>
 * </ul>
 */
@Service
public class AccountResolver {

	private final AccountRepository accountRepository;

	/**
	 * @param accountRepository repositorio de cuentas
	 */
	public AccountResolver(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	/**
	 * Resuelve la cuenta identificada por su numero de cuenta.
	 *
	 * @param accountNumber numero de cuenta recibido en la cabecera
	 * @return la cuenta resuelta
	 * @throws MissingAccountHeaderException si el numero de cuenta viene vacío
	 * @throws AccountNotFoundException si la cuenta no existe
	 */
	@Transactional(readOnly = true)
	public Account resolve(String accountNumber) {
		if (accountNumber == null || accountNumber.isBlank()) {
			throw new MissingAccountHeaderException("Missing required header 'X-AccountNumber'");
		}
		return accountRepository.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new AccountNotFoundException(accountNumber));
	}

}
