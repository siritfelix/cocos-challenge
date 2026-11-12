package ar.cocos.challenge.portfolio.instruments.application;

import java.util.List;
import java.util.Map;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import ar.cocos.challenge.portfolio.instruments.domain.QuotedInstrument;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de consulta del catalogo de instrumentos.
 *
 * <p>Sin parametro de busqueda devuelve el catalogo completo; con el, devuelve
 * los instrumentos que coinciden parcialmente por ticker y/o nombre, sin
 * distinguir mayusculas y sin duplicados (spec FR-005, FR-009, FR-010).</p>
 *
 * <p>Una busqueda vacia o compuesta solo por espacios es un error de
 * solicitud: {@code 400 Bad Request} (spec FR-011).</p>
 *
 * <p>Cada instrumento del resultado se entrega con su cotizacion mas reciente
 * para enriquecer la respuesta con el precio y el cierre anterior (spec 002,
 * FR-001 a FR-003). La obtencion es en lote: una sola consulta por request,
 * independiente del tamano del resultado (SC-006, data-model.md invariante 4).</p>
 */
@Service
public class InstrumentQueryUseCase {

	private final InstrumentRepository instrumentRepository;

	private final MarketDataRepository marketDataRepository;

	/**
	 * @param instrumentRepository repositorio de instrumentos
	 * @param marketDataRepository puerto de cotizaciones de mercado
	 */
	public InstrumentQueryUseCase(InstrumentRepository instrumentRepository,
			MarketDataRepository marketDataRepository) {
		this.instrumentRepository = instrumentRepository;
		this.marketDataRepository = marketDataRepository;
	}

	/**
	 * Devuelve el catalogo de instrumentos o el resultado de una busqueda, cada
	 * instrumento con su cotizacion mas reciente.
	 *
	 * @param query texto de busqueda, o {@code null} para listar todo
	 * @return los instrumentos con su cotizacion (sin cotizacion cuando el
	 *         instrumento no tiene ninguna)
	 * @throws InvalidRequestException si la busqueda viene vacia o solo con
	 *         espacios
	 */
	@Transactional(readOnly = true)
	public List<QuotedInstrument> query(String query) {
		List<Instrument> instruments = resolveInstruments(query);
		Map<Long, LatestQuote> quotes = marketDataRepository
				.findLatestByInstrumentIds(instruments.stream().map(Instrument::id).toList());
		return instruments.stream()
				.map(instrument -> new QuotedInstrument(instrument, quotes.get(instrument.id())))
				.toList();
	}

	/**
	 * Resuelve el listado completo o la busqueda segun el parametro.
	 *
	 * @param query texto de busqueda, o {@code null} para listar todo
	 * @return los instrumentos que responden a la consulta
	 */
	private List<Instrument> resolveInstruments(String query) {
		if (query == null) {
			return instrumentRepository.findAll();
		}
		if (query.isBlank()) {
			throw new InvalidRequestException("Parameter 'query' must not be blank");
		}
		return instrumentRepository.search(query.trim());
	}

}
