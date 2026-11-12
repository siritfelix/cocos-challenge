package ar.cocos.challenge.portfolio.instruments.application;

import java.util.List;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
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
 */
@Service
public class InstrumentQueryUseCase {

	private final InstrumentRepository instrumentRepository;

	/**
	 * @param instrumentRepository repositorio de instrumentos
	 */
	public InstrumentQueryUseCase(InstrumentRepository instrumentRepository) {
		this.instrumentRepository = instrumentRepository;
	}

	/**
	 * Devuelve el catalogo de instrumentos.
	 *
	 * @param query texto de busqueda, o {@code null} para listar todo
	 * @return los instrumentos del catalogo o los que coinciden con la busqueda
	 * @throws InvalidRequestException si la busqueda viene vacia o solo con
	 *         espacios
	 */
	@Transactional(readOnly = true)
	public List<Instrument> query(String query) {
		if (query == null) {
			return instrumentRepository.findAll();
		}
		if (query.isBlank()) {
			throw new InvalidRequestException("Parameter 'query' must not be blank");
		}
		return instrumentRepository.search(query.trim());
	}

}
