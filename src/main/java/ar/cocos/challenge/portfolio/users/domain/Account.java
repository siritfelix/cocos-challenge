package ar.cocos.challenge.portfolio.users.domain;

/**
 * Cuenta habilitada para operar.
 *
 * <p>Es la identidad de negocio de la aplicacion: la fila correspondiente en
 * la tabla {@code users} actua ademas como mutex de la cuenta, ya que todas
 * las operaciones que consumen recursos comparten su bloqueo pesimista
 * (research R-004).</p>
 *
 * @param id identificador interno de la cuenta
 * @param email correo de la cuenta
 * @param accountNumber numero de cuenta, que es el identificador que viaja en
 *        la cabecera {@code X-AccountNumber}
 */
public record Account(Long id, String email, String accountNumber) {

}
