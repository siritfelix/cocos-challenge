package ar.cocos.challenge.portfolio.instruments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import ar.cocos.challenge.portfolio.instruments.domain.QuotedInstrument;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitarios de la consulta del catalogo de instrumentos.
 *
 * <p>Cubren los escenarios 4 a 8 de la seccion 18 de la spec y el
 * enriquecimiento con precios de la spec 002 (FR-001 a FR-003, SC-006).</p>
 */
@ExtendWith(MockitoExtension.class)
class InstrumentQueryUseCaseTest {

	@Mock
	private InstrumentRepository instrumentRepository;

	@Mock
	private MarketDataRepository marketDataRepository;

	@InjectMocks
	private InstrumentQueryUseCase instrumentQueryUseCase;

	@Test
	@DisplayName("Sin busqueda devuelve el catalogo completo con su cotizacion mas reciente")
	void returnsWholeCatalogWithQuotesWithoutQuery() {
		List<Instrument> catalog = List.of(
				new Instrument(1L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES),
				new Instrument(2L, "ARS", "Peso argentino", InstrumentType.MONEDA));
		given(instrumentRepository.findAll()).willReturn(catalog);
		given(marketDataRepository.findLatestByInstrumentIds(anyList())).willReturn(Map.of());

		List<QuotedInstrument> result = instrumentQueryUseCase.query(null);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).instrument().id()).isEqualTo(1L);
		assertThat(result.get(0).quote()).isNull();
		assertThat(result.get(1).instrument().id()).isEqualTo(2L);
		assertThat(result.get(1).quote()).isNull();
	}

	@Test
	@DisplayName("El enriquecimiento obtiene las cotizaciones en una sola consulta en lote")
	void enrichesWithSingleBatchCall() {
		List<Instrument> catalog = List.of(
				new Instrument(1L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES),
				new Instrument(2L, "ARS", "Peso argentino", InstrumentType.MONEDA));
		LatestQuote quote = new LatestQuote(new BigDecimal("7837.50"), new BigDecimal("7975.05"),
				LocalDateTime.of(2023, 7, 14, 0, 0));
		given(instrumentRepository.findAll()).willReturn(catalog);
		given(marketDataRepository.findLatestByInstrumentIds(List.of(1L, 2L))).willReturn(Map.of(1L, quote));

		List<QuotedInstrument> result = instrumentQueryUseCase.query(null);

		verify(marketDataRepository).findLatestByInstrumentIds(List.of(1L, 2L));
		assertThat(result.get(0).quote()).isSameAs(quote);
		assertThat(result.get(1).quote()).isNull();
	}

	@Test
	@DisplayName("Con busqueda filtra por coincidencia de ticker y/o nombre")
	void filtersByQuery() {
		List<Instrument> matches = List.of(
				new Instrument(1L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES));
		given(instrumentRepository.search("ypf")).willReturn(matches);
		given(marketDataRepository.findLatestByInstrumentIds(anyList())).willReturn(Map.of());

		List<QuotedInstrument> result = instrumentQueryUseCase.query("ypf");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).instrument().id()).isEqualTo(1L);
	}

	@Test
	@DisplayName("La busqueda se normaliza quitando los espacios sobrantes")
	void trimsQuery() {
		given(instrumentRepository.search("ypf")).willReturn(List.of());
		given(marketDataRepository.findLatestByInstrumentIds(anyList())).willReturn(Map.of());

		instrumentQueryUseCase.query("  ypf  ");

		verify(instrumentRepository).search("ypf");
	}

	@Test
	@DisplayName("Una busqueda vacia se rechaza por validacion")
	void rejectsBlankQuery() {
		assertThatThrownBy(() -> instrumentQueryUseCase.query(""))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("query");

		assertThatThrownBy(() -> instrumentQueryUseCase.query("   "))
				.isInstanceOf(InvalidRequestException.class);

		verify(instrumentRepository, never()).search(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("Una busqueda sin coincidencias devuelve una lista vacia")
	void returnsEmptyListWhenNoMatches() {
		given(instrumentRepository.search("zzz")).willReturn(List.of());
		given(marketDataRepository.findLatestByInstrumentIds(anyList())).willReturn(Map.of());

		assertThat(instrumentQueryUseCase.query("zzz")).isEmpty();
	}

}
