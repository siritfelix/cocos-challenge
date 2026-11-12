package ar.cocos.challenge.portfolio.orders.domain;

import java.time.LocalDateTime;

/**
 * Registro de idempotencia de una orden.
 *
 * <p>Asocia una clave de idempotencia de una cuenta con la orden que produjo,
 * junto con la huella del contenido de la solicitud (spec seccion 3.5).</p>
 *
 * @param id identificador interno del registro
 * @param accountId identificador de la cuenta a la que pertenece la clave
 * @param key clave de idempotencia enviada por el cliente
 * @param requestHash huella SHA-256 del contenido semantico de la solicitud
 * @param orderId identificador de la orden asociada
 * @param createdAt instante de creacion del registro
 */
public record IdempotencyKey(Long id, Long accountId, String key, String requestHash, Long orderId,
		LocalDateTime createdAt) {

}
