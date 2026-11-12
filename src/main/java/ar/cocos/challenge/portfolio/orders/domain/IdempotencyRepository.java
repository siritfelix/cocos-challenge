package ar.cocos.challenge.portfolio.orders.domain;

import java.util.Optional;

/**
 * Puerto de acceso a los registros de idempotencia.
 */
public interface IdempotencyRepository {

	/**
	 * Busca el registro de idempotencia de una cuenta para una clave.
	 *
	 * @param accountId identificador de la cuenta
	 * @param key clave de idempotencia
	 * @return el registro, o vacio si la clave no fue usada por esa cuenta
	 */
	Optional<IdempotencyKey> findByAccountIdAndKey(Long accountId, String key);

	/**
	 * Persiste un registro de idempotencia.
	 *
	 * @param idempotencyKey registro a persistir
	 * @return el registro persistido
	 */
	IdempotencyKey save(IdempotencyKey idempotencyKey);

}
