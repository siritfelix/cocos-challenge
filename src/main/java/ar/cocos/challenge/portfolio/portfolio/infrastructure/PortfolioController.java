package ar.cocos.challenge.portfolio.portfolio.infrastructure;

import ar.cocos.challenge.portfolio.portfolio.application.PortfolioQueryUseCase;
import ar.cocos.challenge.portfolio.portfolio.application.PortfolioResponse;
import ar.cocos.challenge.portfolio.portfolio.domain.Portfolio;
import ar.cocos.challenge.portfolio.users.domain.Account;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de consulta del portfolio.
 *
 * <p>La cuenta se resuelve con el argument resolver de la cabecera
 * {@code X-AccountNumber}, de modo que la ausencia de la cabecera produce
 * {@code 400} y una cuenta inexistente {@code 404} sin duplicar esa logica en
 * el controller (spec FR-002, FR-003).</p>
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
	 * @param account cuenta identificada por la cabecera
	 * @return el portfolio de la cuenta
	 */
	@GetMapping
	public ResponseEntity<PortfolioResponse> getPortfolio(Account account) {
		Portfolio portfolio = portfolioQueryUseCase.query(account.id());
		return ResponseEntity.ok(portfolioMapper.toResponse(portfolio));
	}

}
