package ar.cocos.challenge.portfolio.users.domain;

import java.util.Optional;

/**
 * Puerto de acceso a las cuentas.
 *
 * <p>La capa de dominio define la interfaz y la infraestructura la implementa
 * sobre Spring Data JPA (Principio V).</p>
 */
public interface AccountRepository {

	/**
	 * Busca una cuenta por su numero de cuenta.
	 *
	 * @param accountNumber numero de cuenta
	 * @return la cuenta, o vacio si no existe
	 */
	Optional<Account> findByAccountNumber(String accountNumber);

	/**
	 * Busca una cuenta por su numero de cuenta y toma un bloqueo pesimista
	 * sobre su fila.
	 *
	 * <p>El bloqueo serializa a las operaciones concurrentes de la misma
	 * cuenta, de modo que no puedan consumir los mismos recursos. En PostgreSQL
	 * se traduce a {@code FOR NO KEY UPDATE}, compatible con el
	 * {@code FOR KEY SHARE} que toma la verificacion de clave foranea al
	 * insertar ordenes (research R-004).</p>
	 *
	 * <p>Debe invocarse dentro de una transaccion activa.</p>
	 *
	 * @param accountNumber numero de cuenta
	 * @return la cuenta bloqueada, o vacio si no existe
	 */
	Optional<Account> findByAccountNumberForUpdate(String accountNumber);

}
