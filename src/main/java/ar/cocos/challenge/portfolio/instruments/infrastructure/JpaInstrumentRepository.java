package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data de la tabla {@code instruments}.
 */
public interface JpaInstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

	/**
	 * Busca un instrumento por su ticker exacto.
	 *
	 * @param ticker simbolo del instrumento
	 * @return la entidad del instrumento, o vacio si no existe
	 */
	Optional<InstrumentEntity> findByTicker(String ticker);

	/**
	 * Devuelve el catalogo completo, ordenado por ticker.
	 *
	 * <p>El orden es determinista para que la API devuelva siempre el mismo
	 * listado y los tests puedan verificarlo.</p>
	 *
	 * @return las entidades del catalogo, ordenadas por ticker
	 */
	List<InstrumentEntity> findAllByOrderByTickerAsc();

	/**
	 * Busca instrumentos por coincidencia parcial de ticker y/o de nombre.
	 *
	 * <p>La condicion es una disyuncion sobre la MISMA fila, por lo que un
	 * instrumento que coincide a la vez por ticker y por nombre aparece una
	 * sola vez, sin duplicados (spec FR-010).</p>
	 *
	 * @param query texto de busqueda
	 * @return las entidades que coinciden
	 */
	@Query("select i from InstrumentEntity i "
			+ "where lower(i.ticker) like lower(concat('%', :query, '%')) "
			+ "or lower(i.name) like lower(concat('%', :query, '%')) "
			+ "order by i.ticker")
	List<InstrumentEntity> search(@Param("query") String query);

}
