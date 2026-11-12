package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data de la tabla {@code marketdata}.
 */
public interface JpaMarketDataRepository extends JpaRepository<MarketDataEntity, Long> {

	/**
	 * Devuelve la cotizacion mas reciente de un instrumento.
	 *
	 * <p>La ultima cotizacion es la fila con mayor {@code datetime}
	 * (spec seccion 3.4).</p>
	 *
	 * @param instrumentId identificador del instrumento
	 * @return la cotizacion mas reciente, o vacio si no hay ninguna
	 */
	Optional<MarketDataEntity> findFirstByInstrumentIdOrderByDatetimeDesc(Long instrumentId);

}
