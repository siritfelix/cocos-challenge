package ar.cocos.challenge.portfolio.users.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code users}.
 *
 * <p>Vive en la capa de infraestructura y nunca se expone fuera de ella
 * (Principio IV).</p>
 */
@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "email", nullable = false, unique = true, length = 150)
	private String email;

	@Column(name = "account_number", nullable = false, unique = true, length = 50)
	private String accountNumber;

	protected UserEntity() {
		// Constructor requerido por JPA.
	}

	/**
	 * @param id identificador interno de la cuenta
	 * @param email correo de la cuenta
	 * @param accountNumber numero de cuenta
	 */
	public UserEntity(Long id, String email, String accountNumber) {
		this.id = id;
		this.email = email;
		this.accountNumber = accountNumber;
	}

	/**
	 * @return el identificador interno de la cuenta
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return el correo de la cuenta
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * @return el numero de cuenta
	 */
	public String getAccountNumber() {
		return accountNumber;
	}

}
