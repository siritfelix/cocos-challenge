package ar.cocos.challenge.portfolio.instruments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;
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
 * <p>Cubren los escenarios 4 a 8 de la seccion 18 de la spec.</p>
 */
@ExtendWith(MockitoExtension.class)
class InstrumentQueryUseCaseTest {

	@Mock
	private InstrumentRepository instrumentRepository;

	@InjectMocks
	private InstrumentQueryUseCase instrumentQueryUseCase;

	@Test
	@DisplayName("Sin busqueda devuelve el catalogo completo")
	void returnsWholeCatalogWithoutQuery() {
		List<Instrument> catalog = List.of(
				new Instrument(1L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES),
				new Instrument(2L, "ARS", "Peso argentino", InstrumentType.MONEDA));
		given(instrumentRepository.findAll()).willReturn(catalog);

		assertThat(instrumentQueryUseCase.query(null)).isEqualTo(catalog);
	}

	@Test
	@DisplayName("Con busqueda filtra por coincidencia de ticker y/o nombre")
	void filtersByQuery() {
		List<Instrument> matches = List.of(
				new Instrument(1L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES));
		given(instrumentRepository.search("ypf")).willReturn(matches);

		assertThat(instrumentQueryUseCase.query("ypf")).isEqualTo(matches);
	}

	@Test
	@DisplayName("La busqueda se normaliza quitando los espacios sobrantes")
	void trimsQuery() {
		given(instrumentRepository.search("ypf")).willReturn(List.of());

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

		assertThat(instrumentQueryUseCase.query("zzz")).isEmpty();
	}

}
