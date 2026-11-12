package ar.cocos.challenge.portfolio.portfolio.infrastructure;

import ar.cocos.challenge.portfolio.portfolio.application.PortfolioQueryUseCase;
import ar.cocos.challenge.portfolio.portfolio.application.PortfolioResponse;
import ar.cocos.challenge.portfolio.portfolio.domain.Portfolio;
import ar.cocos.challenge.portfolio.users.application.AccountResolver;
import ar.cocos.challenge.portfolio.users.domain.Account;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

	/**
	 * Nombre de la cabecera que transporta el numero de cuenta.
	 */
	private static final String ACCOUNT_HEADER = "X-AccountNumber";

	private final PortfolioQueryUseCase portfolioQueryUseCase;
	private final PortfolioMapper portfolioMapper;
	private final AccountResolver accountResolver;

	/**
	 * @param portfolioQueryUseCase caso de uso de consulta del portfolio
	 * @param portfolioMapper mapeador entre dominio y DTOs
	 * @param accountResolver resolutor de la cuenta por su numero
	 */
	public PortfolioController(PortfolioQueryUseCase portfolioQueryUseCase, PortfolioMapper portfolioMapper,
			AccountResolver accountResolver) {
		this.portfolioQueryUseCase = portfolioQueryUseCase;
		this.portfolioMapper = portfolioMapper;
		this.accountResolver = accountResolver;
	}

	/**
	 * Devuelve el estado patrimonial de la cuenta.
	 *
	 * @param accountNumber numero de cuenta de la cabecera
	 * @return el portfolio de la cuenta
	 */
	@GetMapping
	public ResponseEntity<PortfolioResponse> getPortfolio(
			@RequestHeader(value = ACCOUNT_HEADER, required = true) String accountNumber) {
		Account account = accountResolver.resolve(accountNumber);
		Portfolio portfolio = portfolioQueryUseCase.query(account.id());
		return ResponseEntity.ok(portfolioMapper.toResponse(portfolio));
	}

}
