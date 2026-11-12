package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code idempotency_keys}.
 *
 * <p>La restriccion unica {@code (user_id, idempotency_key)} garantiza que una
 * misma clave no pueda usarse dos veces por la misma cuenta (spec FR-055).</p>
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, length = 255)
	private String key;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "order_id", nullable = false, unique = true)
	private Long orderId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected IdempotencyKeyEntity() {
		// Constructor requerido por JPA.
	}

	/**
	 * @return el identificador del registro
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return el identificador de la cuenta
	 */
	public Long getUserId() {
		return userId;
	}

	/**
	 * @return la clave de idempotencia
	 */
	public String getKey() {
		return key;
	}

	/**
	 * @return la huella del contenido de la solicitud
	 */
	public String getRequestHash() {
		return requestHash;
	}

	/**
	 * @return el identificador de la orden asociada
	 */
	public Long getOrderId() {
		return orderId;
	}

	/**
	 * @return el instante de creacion del registro
	 */
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/**
	 * @param userId identificador de la cuenta
	 */
	public void setUserId(Long userId) {
		this.userId = userId;
	}

	/**
	 * @param key clave de idempotencia
	 */
	public void setKey(String key) {
		this.key = key;
	}

	/**
	 * @param requestHash huella del contenido de la solicitud
	 */
	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	/**
	 * @param orderId identificador de la orden asociada
	 */
	public void setOrderId(Long orderId) {
		this.orderId = orderId;
	}

	/**
	 * @param createdAt instante de creacion del registro
	 */
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
