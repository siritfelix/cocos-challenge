package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data de la tabla {@code idempotency_keys}.
 */
public interface JpaIdempotencyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

	/**
	 * Busca el registro de idempotencia de una cuenta para una clave.
	 *
	 * <p>La consulta es explicita porque el atributo de la entidad se llama
	 * {@code key} y la derivacion de Spring Data buscaria una propiedad
	 * {@code idempotencyKey}.</p>
	 *
	 * @param userId identificador de la cuenta
	 * @param idempotencyKey clave de idempotencia
	 * @return la entidad del registro, o vacio si la clave no fue usada
	 */
	@Query("select k from IdempotencyKeyEntity k "
			+ "where k.userId = :userId and k.key = :idempotencyKey")
	Optional<IdempotencyKeyEntity> findByUserIdAndIdempotencyKey(@Param("userId") Long userId,
			@Param("idempotencyKey") String idempotencyKey);

}
