package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * Devuelve, en una sola consulta, la cotizacion mas reciente de cada uno de
	 * los instrumentos indicados.
	 *
	 * <p>Una fila pertenece al resultado cuando su {@code datetime} es el mayor
	 * de las cotizaciones de su propio instrumento: la consulta evita el patron
	 * N+1 al enriquecer el catalogo completo (spec 002, SC-006; research
	 * R-002).</p>
	 *
	 * @param instrumentIds identificadores de los instrumentos consultados
	 * @return una cotizacion por instrumento: la mas reciente de cada uno; los
	 *         instrumentos sin cotizaciones no aportan filas
	 */
	@Query("""
			select md from MarketDataEntity md
			where md.instrumentId in :instrumentIds
			  and md.datetime = (select max(m2.datetime) from MarketDataEntity m2
			                     where m2.instrumentId = md.instrumentId)
			""")
	List<MarketDataEntity> findLatestByInstrumentIds(@Param("instrumentIds") List<Long> instrumentIds);

}
