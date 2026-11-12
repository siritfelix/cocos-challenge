package ar.cocos.challenge.portfolio.instruments.infrastructure;

import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code instruments}.
 */
@Entity
@Table(name = "instruments")
public class InstrumentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "ticker", nullable = false, unique = true, length = 20)
	private String ticker;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private InstrumentType type;

	protected InstrumentEntity() {
		// Constructor requerido por JPA.
	}

	/**
	 * @param id identificador interno del instrumento
	 * @param ticker simbolo del instrumento
	 * @param name nombre descriptivo del instrumento
	 * @param type tipo del instrumento
	 */
	public InstrumentEntity(Long id, String ticker, String name, InstrumentType type) {
		this.id = id;
		this.ticker = ticker;
		this.name = name;
		this.type = type;
	}

	/**
	 * @return el identificador interno del instrumento
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return el simbolo del instrumento
	 */
	public String getTicker() {
		return ticker;
	}

	/**
	 * @return el nombre descriptivo del instrumento
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return el tipo del instrumento
	 */
	public InstrumentType getType() {
		return type;
	}

}
