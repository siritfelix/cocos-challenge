package ar.cocos.challenge.portfolio.users.infrastructure;

import ar.cocos.challenge.portfolio.users.application.AccountResolver;
import ar.cocos.challenge.portfolio.users.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resuelve el parametro de tipo {@link Account} de los controllers a partir de
 * la cabecera {@code X-AccountNumber}.
 *
 * <p>Permite que los controllers reciben la cuenta ya resuelta sin repetir la
 * busqueda ni el manejo de errores en cada endpoint, manteniendo los
 * controllers como adaptadores delgados (Principio V).</p>
 */
@Component
public class AccountHeaderArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * Nombre de la cabecera que transporta el numero de cuenta.
	 */
	public static final String ACCOUNT_HEADER = "X-AccountNumber";

	private final AccountResolver accountResolver;

	/**
	 * @param accountResolver caso de uso que resuelve la cuenta
	 */
	public AccountHeaderArgumentResolver(AccountResolver accountResolver) {
		this.accountResolver = accountResolver;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Account.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer modelAndViewContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		String accountNumber = request == null ? null : request.getHeader(ACCOUNT_HEADER);
		return accountResolver.resolve(accountNumber);
	}

}
