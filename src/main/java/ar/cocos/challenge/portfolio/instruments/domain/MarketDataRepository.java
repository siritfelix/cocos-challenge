package ar.cocos.challenge.portfolio.instruments.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de acceso a las cotizaciones de mercado.
 */
public interface MarketDataRepository {

	/**
	 * Devuelve la ultima cotizacion disponible de un instrumento.
	 *
	 * @param instrumentId identificador interno del instrumento
	 * @return la cotizacion mas reciente, o vacio si el instrumento no tiene
	 *         ninguna
	 */
	Optional<LatestQuote> findLatestByInstrumentId(Long instrumentId);

	/**
	 * Devuelve la ultima cotizacion de cada uno de los instrumentos indicados.
	 *
	 * <p>La consulta se resuelve en una sola operacion, independiente de la
	 * cantidad de instrumentos: enriquecer el catalogo completo no debe
	 * degradarse con la cantidad de filas (spec 002, SC-006).</p>
	 *
	 * @param instrumentIds identificadores de los instrumentos consultados
	 * @return mapa con la cotizacion mas reciente de cada instrumento que la
	 *         tenga; los identificadores sin cotizacion no aparecen
	 */
	Map<Long, LatestQuote> findLatestByInstrumentIds(List<Long> instrumentIds);

}
