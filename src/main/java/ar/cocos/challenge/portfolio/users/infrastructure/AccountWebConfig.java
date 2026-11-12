package ar.cocos.challenge.portfolio.users.infrastructure;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el resolutor del parametro {@code Account} en la capa web.
 *
 * <p>Permite que los controllers declaren un parametro de tipo
 * {@link ar.cocos.challenge.portfolio.users.domain.Account} y que se resuelva
 * desde la cabecera {@code X-AccountNumber}.</p>
 */
@Configuration
public class AccountWebConfig implements WebMvcConfigurer {

	private final AccountHeaderArgumentResolver accountHeaderArgumentResolver;

	/**
	 * @param accountHeaderArgumentResolver resolutor de la cabecera de cuenta
	 */
	public AccountWebConfig(AccountHeaderArgumentResolver accountHeaderArgumentResolver) {
		this.accountHeaderArgumentResolver = accountHeaderArgumentResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(accountHeaderArgumentResolver);
	}

}
