package ar.cocos.challenge.portfolio.users.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data de la tabla {@code users}.
 */
public interface JpaAccountRepository extends JpaRepository<UserEntity, Long> {

	/**
	 * Busca una cuenta por su numero de cuenta.
	 *
	 * @param accountNumber numero de cuenta
	 * @return la entidad de la cuenta, o vacio si no existe
	 */
	Optional<UserEntity> findByAccountNumber(String accountNumber);

	/**
	 * Busca una cuenta tomando un bloqueo pesimista de escritura sobre su fila.
	 *
	 * <p>Hibernate traduce {@code PESSIMISTIC_WRITE} a {@code FOR NO KEY UPDATE}
	 * en PostgreSQL, que se autoexcluye y por tanto serializa a las dos
	 * operaciones concurrentes de la misma cuenta (research R-004).</p>
	 *
	 * @param accountNumber numero de cuenta
	 * @return la entidad bloqueada, o vacio si no existe
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from UserEntity u where u.accountNumber = :accountNumber")
	Optional<UserEntity> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

}
