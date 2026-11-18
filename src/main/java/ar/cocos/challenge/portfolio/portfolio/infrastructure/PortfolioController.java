package ar.cocos.challenge.portfolio.portfolio.infrastructure;

import ar.cocos.challenge.portfolio.portfolio.application.PortfolioQueryUseCase;
import ar.cocos.challenge.portfolio.portfolio.application.PortfolioResponse;
import ar.cocos.challenge.portfolio.portfolio.domain.Portfolio;
import ar.cocos.challenge.portfolio.shared.infrastructure.ErrorResponse;
import ar.cocos.challenge.portfolio.users.domain.Account;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de consulta del portfolio.
 *
 * <p>La cuenta se identifica con la cabecera obligatoria
 * {@code X-AccountNumber} y se resuelve por su numero: la ausencia de la
 * cabecera produce {@code 400} y una cuenta inexistente {@code 404}
 * (spec FR-002, FR-003).</p>
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

	private final PortfolioQueryUseCase portfolioQueryUseCase;
	private final PortfolioMapper portfolioMapper;

	/**
	 * @param portfolioQueryUseCase caso de uso de consulta del portfolio
	 * @param portfolioMapper mapeador entre dominio y DTOs
	 */
	public PortfolioController(PortfolioQueryUseCase portfolioQueryUseCase, PortfolioMapper portfolioMapper) {
		this.portfolioQueryUseCase = portfolioQueryUseCase;
		this.portfolioMapper = portfolioMapper;
	}

	/**
	 * Devuelve el estado patrimonial de la cuenta.
	 *
	 * @param account contexto de la cuenta
	 * @return el portfolio de la cuenta
	 */
	@GetMapping
	@Parameters({
			@Parameter(name = "X-AccountNumber", in = ParameterIn.HEADER, required = true,
					description = "Numero de cuenta", schema = @Schema(type = "string"))})
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "Solicitud invalida o error de validacion",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cuenta inexistente",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
	public ResponseEntity<PortfolioResponse> getPortfolio(
			Account account) {
		Portfolio portfolio = portfolioQueryUseCase.query(account);
		return ResponseEntity.ok(portfolioMapper.toResponse(portfolio));
	}
}
