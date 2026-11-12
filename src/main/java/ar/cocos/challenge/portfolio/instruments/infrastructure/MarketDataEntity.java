package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla {@code marketdata}.
 *
 * <p>Los importes usan {@code NUMERIC(10,2)} y se mapean con precision y escala
 * explicitas para no depender del valor por defecto de Hibernate
 * (research R-008).</p>
 */
@Entity
@Table(name = "marketdata")
public class MarketDataEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "instrument_id", nullable = false)
	private Long instrumentId;

	@Column(name = "high", nullable = false, precision = 10, scale = 2)
	private BigDecimal high;

	@Column(name = "low", nullable = false, precision = 10, scale = 2)
	private BigDecimal low;

	@Column(name = "open", nullable = false, precision = 10, scale = 2)
	private BigDecimal open;

	@Column(name = "close", nullable = false, precision = 10, scale = 2)
	private BigDecimal close;

	@Column(name = "previous_close", nullable = false, precision = 10, scale = 2)
	private BigDecimal previousClose;

	@Column(name = "datetime", nullable = false)
	private LocalDateTime datetime;

	protected MarketDataEntity() {
		// Constructor requerido por JPA.
	}

	/**
	 * @return el identificador interno de la cotizacion
	 */
	public Long getId() {
		return id;
	}

	/**
	 * @return el identificador del instrumento cotizado
	 */
	public Long getInstrumentId() {
		return instrumentId;
	}

	/**
	 * @return el precio maximo de la sesion
	 */
	public BigDecimal getHigh() {
		return high;
	}

	/**
	 * @return el precio minimo de la sesion
	 */
	public BigDecimal getLow() {
		return low;
	}

	/**
	 * @return el precio de apertura
	 */
	public BigDecimal getOpen() {
		return open;
	}

	/**
	 * @return el precio de cierre
	 */
	public BigDecimal getClose() {
		return close;
	}

	/**
	 * @return el cierre anterior
	 */
	public BigDecimal getPreviousClose() {
		return previousClose;
	}

	/**
	 * @return el instante de la cotizacion
	 */
	public LocalDateTime getDatetime() {
		return datetime;
	}

}
