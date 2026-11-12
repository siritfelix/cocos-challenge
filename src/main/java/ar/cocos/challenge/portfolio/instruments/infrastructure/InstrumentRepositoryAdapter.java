package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;
import java.util.Optional;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link InstrumentRepository} sobre Spring Data JPA.
 */
@Repository
public class InstrumentRepositoryAdapter implements InstrumentRepository {

	private final JpaInstrumentRepository jpaInstrumentRepository;

	/**
	 * @param jpaInstrumentRepository repositorio Spring Data
	 */
	public InstrumentRepositoryAdapter(JpaInstrumentRepository jpaInstrumentRepository) {
		this.jpaInstrumentRepository = jpaInstrumentRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Instrument> findById(Long instrumentId) {
		return jpaInstrumentRepository.findById(instrumentId).map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Instrument> findByTicker(String ticker) {
		return jpaInstrumentRepository.findByTicker(ticker).map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Instrument> findAll() {
		return jpaInstrumentRepository.findAllByOrderByTickerAsc().stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Instrument> search(String query) {
		return jpaInstrumentRepository.search(query).stream().map(this::toDomain).toList();
	}

	private Instrument toDomain(InstrumentEntity entity) {
		return new Instrument(entity.getId(), entity.getTicker(), entity.getName(), entity.getType());
	}

}
