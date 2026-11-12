package ar.cocos.challenge.portfolio.shared.domain;

/**
 * Error de dominio que se traduce a {@code 404 Not Found}.
 *
 * <p>Indica que la cuenta identificada por el numero de cuenta no existe
 * (spec FR-003).</p>
 */
public class AccountNotFoundException extends DomainException {

	/**
	 * @param accountNumber numero de cuenta buscado
	 */
	public AccountNotFoundException(String accountNumber) {
		super("Account not found: " + accountNumber);
	}

}
