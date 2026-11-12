package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;

import ar.cocos.challenge.portfolio.instruments.application.InstrumentQueryUseCase;
import ar.cocos.challenge.portfolio.instruments.application.InstrumentResponse;
import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.shared.infrastructure.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de consulta del catalogo de instrumentos.
 *
 * <p>No requiere la cabecera de cuenta: el catalogo es comun a todos
 * (spec FR-006).</p>
 */
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

	private final InstrumentQueryUseCase instrumentQueryUseCase;
	private final InstrumentMapper instrumentMapper;

	/**
	 * @param instrumentQueryUseCase caso de uso de consulta del catalogo
	 * @param instrumentMapper mapeador entre dominio y DTOs
	 */
	public InstrumentController(InstrumentQueryUseCase instrumentQueryUseCase, InstrumentMapper instrumentMapper) {
		this.instrumentQueryUseCase = instrumentQueryUseCase;
		this.instrumentMapper = instrumentMapper;
	}

	/**
	 * Devuelve el catalogo de instrumentos o el resultado de una busqueda.
	 *
	 * @param query texto de busqueda opcional; si viene vacio o solo con
	 *        espacios, la solicitud se rechaza con {@code 400}
	 * @return el catalogo o los instrumentos que coinciden
	 */
	@GetMapping
	@ApiResponses(@ApiResponse(responseCode = "400", description = "Solicitud invalida o error de validacion",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))))
	public ResponseEntity<List<InstrumentResponse>> getInstruments(
			@RequestParam(required = false) String query) {
		List<Instrument> instruments = instrumentQueryUseCase.query(query);
		return ResponseEntity.ok(instrumentMapper.toResponses(instruments));
	}

}
