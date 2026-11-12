package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.Optional;

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

	private LatestQuote toDomain(MarketDataEntity entity) {
		return new LatestQuote(entity.getClose(), entity.getPreviousClose(), entity.getDatetime());
	}

}
