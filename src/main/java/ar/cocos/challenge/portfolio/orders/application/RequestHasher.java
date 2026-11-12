package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;
/**
 * Calcula la huella criptografica del contenido semantico de una solicitud.
 *
 * <p>La huella se construye sobre una canonicalizacion explicita del contenido
 * relevante (ticker, lado, tipo, cantidad o importe y precio), de modo que el
 * resultado NO dependa del orden de las propiedades del JSON recibido
 * (spec FR-056).</p>
 *
 * <p>La canonicalizacion normaliza los valores: el ticker en mayusculas y sin
 * espacios, los enum como nombre, y los numeros con {@code stripTrailingZeros}
 * para que {@code 10} y {@code 10.00} produzcan la misma huella.</p>
 */
@Component
public class RequestHasher {

	/**
	 * Separador entre los campos canonicalizados. Es un caracter que no puede
	 * aparecer en ninguno de los valores canonicalizados, de modo que la
	 * concatenacion es inequivoca.
	 */
	private static final String SEPARATOR = "|";

	/**
	 * Calcula la huella SHA-256 de la solicitud.
	 *
	 * @param request solicitud de creacion de orden
	 * @return la huella en hexadecimal, de 64 caracteres
	 */
	public String hash(CreateOrderRequest request) {
		String canonical = canonicalize(request);
		return sha256(canonical);
	}

	/**
	 * Construye la representacion canonica del contenido semantico.
	 *
	 * @param request solicitud de creacion de orden
	 * @return la cadena canonica
	 */
	String canonicalize(CreateOrderRequest request) {
		StringBuilder canonical = new StringBuilder();
		append(canonical, request.getTicker() == null ? null : request.getTicker().trim().toUpperCase());
		append(canonical, request.getSide() == null ? null : request.getSide().name());
		append(canonical, request.getType() == null ? null : request.getType().name());
		// La cantidad y el importe son excluyentes entre si, pero se incluyen
		// ambos campos para que la huella distinga 'size' de 'amount'.
		append(canonical, normalize(request.getSize()));
		append(canonical, normalize(request.getAmount()));
		append(canonical, normalize(request.getPrice()));
		return canonical.toString();
	}

	private void append(StringBuilder canonical, String value) {
		canonical.append(value == null ? "" : value).append(SEPARATOR);
	}

	/**
	 * Normaliza un valor numerico a su representacion textual canonica.
	 *
	 * @param value valor a normalizar
	 * @return la representacion canonica, o cadena vacia si el valor es nulo
	 */
	private String normalize(BigDecimal value) {
		if (value == null) {
			return "";
		}
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * Calcula el SHA-256 de una cadena.
	 *
	 * @param content contenido a hashear
	 * @return la huella en hexadecimal
	 */
	private String sha256(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is not available", exception);
		}
	}

}
