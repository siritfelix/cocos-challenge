package ar.cocos.challenge.portfolio.instruments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifica el acceso al catalogo de instrumentos y a las cotizaciones contra
 * la base de datos real.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InstrumentCatalogIT {

	@Autowired
	private InstrumentRepository instrumentRepository;

	@Autowired
	private MarketDataRepository marketDataRepository;

	@Test
	@DisplayName("Devuelve el catalogo completo con el instrumento de efectivo incluido")
	void findsAllInstrumentsIncludingCash() {
		List<Instrument> instruments = instrumentRepository.findAll();

		assertThat(instruments).hasSize(66);
		assertThat(instruments).extracting(Instrument::ticker).contains("ARS");
		assertThat(instruments).filteredOn(Instrument::isCash).hasSize(1);
	}

	@Test
	@DisplayName("Busca un instrumento por su ticker exacto")
	void findsInstrumentByTicker() {
		assertThat(instrumentRepository.findByTicker("YPFD")).isPresent()
				.get()
				.satisfies(instrument -> {
					assertThat(instrument.ticker()).isEqualTo("YPFD");
					assertThat(instrument.type().name()).isEqualTo("ACCIONES");
					assertThat(instrument.isTradeable()).isTrue();
				});
	}

	@Test
	@DisplayName("Busca instrumentos por coincidencia parcial de ticker sin distinguir mayusculas")
	void searchesByPartialTickerIgnoringCase() {
		List<Instrument> results = instrumentRepository.search("ypf");

		assertThat(results).isNotEmpty();
		assertThat(results).allSatisfy(instrument ->
				assertThat(instrument.ticker().toLowerCase()).contains("ypf"));
	}

	@Test
	@DisplayName("Busca instrumentos por coincidencia parcial de nombre")
	void searchesByPartialName() {
		List<Instrument> results = instrumentRepository.search("macro");

		assertThat(results).isNotEmpty();
		assertThat(results).extracting(Instrument::ticker).contains("BMA");
	}

	@Test
	@DisplayName("No devuelve duplicados cuando coincide por ticker y por nombre")
	void doesNotReturnDuplicates() {
		List<Instrument> results = instrumentRepository.search("capex");

		assertThat(results).extracting(Instrument::ticker).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("Devuelve una lista vacia cuando la busqueda no tiene coincidencias")
	void returnsEmptyListWhenNoMatches() {
		assertThat(instrumentRepository.search("zzzzz")).isEmpty();
	}

	@Test
	@DisplayName("Devuelve la ultima cotizacion del instrumento con su cierre anterior")
	void findsLatestQuote() {
		Long ypfdId = instrumentRepository.findByTicker("YPFD").orElseThrow().id();

		LatestQuote quote = marketDataRepository.findLatestByInstrumentId(ypfdId).orElseThrow();

		assertThat(quote.close()).isPositive();
		assertThat(quote.previousClose()).isPositive();
		assertThat(quote.isAvailable()).isTrue();
	}

	@Test
	@DisplayName("Devuelve vacio para un instrumento sin cotizaciones")
	void returnsEmptyQuoteWhenInstrumentHasNoMarketData() {
		Long arsId = instrumentRepository.findByTicker("ARS").orElseThrow().id();

		assertThat(marketDataRepository.findLatestByInstrumentId(arsId)).isEmpty();
	}

}
