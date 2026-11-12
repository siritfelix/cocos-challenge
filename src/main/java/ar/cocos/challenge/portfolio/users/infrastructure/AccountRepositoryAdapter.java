package ar.cocos.challenge.portfolio.users.infrastructure;

import java.util.Optional;

import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link AccountRepository} sobre Spring Data JPA.
 *
 * <p>Traduce entre la entidad de infraestructura y el objeto de dominio, de
 * modo que la capa de dominio no conoce JPA (Principio V).</p>
 */
@Repository
public class AccountRepositoryAdapter implements AccountRepository {

	private final JpaAccountRepository jpaAccountRepository;

	/**
	 * @param jpaAccountRepository repositorio Spring Data
	 */
	public AccountRepositoryAdapter(JpaAccountRepository jpaAccountRepository) {
		this.jpaAccountRepository = jpaAccountRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Account> findByAccountNumber(String accountNumber) {
		return jpaAccountRepository.findByAccountNumber(accountNumber).map(this::toDomain);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>La propagacion es {@code MANDATORY}: el bloqueo solo tiene sentido
	 * dentro de una transaccion que lo mantenga hasta el commit. Si se exigiera
	 * una transaccion propia, el bloqueo se liberaria al retornar del metodo y
	 * las operaciones concurrentes no quedarian serializadas.</p>
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Account> findByAccountNumberForUpdate(String accountNumber) {
		return jpaAccountRepository.findByAccountNumberForUpdate(accountNumber).map(this::toDomain);
	}

	private Account toDomain(UserEntity entity) {
		return new Account(entity.getId(), entity.getEmail(), entity.getAccountNumber());
	}

}
