package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link MarketDataRepository} sobre Spring Data JPA.
 */
@Repository
public class MarketDataRepositoryAdapter implements MarketDataRepository {

	private final JpaMarketDataRepository jpaMarketDataRepository;

	/**
	 * @param jpaMarketDataRepository repositorio Spring Data
	 */
	public MarketDataRepositoryAdapter(JpaMarketDataRepository jpaMarketDataRepository) {
		this.jpaMarketDataRepository = jpaMarketDataRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LatestQuote> findLatestByInstrumentId(Long instrumentId) {
		return jpaMarketDataRepository.findFirstByInstrumentIdOrderByDatetimeDesc(instrumentId)
				.map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, LatestQuote> findLatestByInstrumentIds(List<Long> instrumentIds) {
		if (instrumentIds.isEmpty()) {
			return Map.of();
		}
		// La consulta ya devuelve como maximo una fila por instrumento: es la
		// mas reciente de cada uno, asi que el mapa se arma directamente.
		return jpaMarketDataRepository.findLatestByInstrumentIds(instrumentIds).stream()
				.collect(Collectors.toMap(MarketDataEntity::getInstrumentId, this::toDomain,
						(first, second) -> first));
	}

	private LatestQuote toDomain(MarketDataEntity entity) {
		return new LatestQuote(entity.getClose(), entity.getPreviousClose(), entity.getDatetime());
	}

}
