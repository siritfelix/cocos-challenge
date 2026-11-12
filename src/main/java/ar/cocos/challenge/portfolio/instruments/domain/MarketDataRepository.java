package ar.cocos.challenge.portfolio.instruments.domain;

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

}
