package ar.cocos.challenge.portfolio.shared.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de la documentacion OpenAPI de la API.
 *
 * <p>El contrato se publica de forma autogenerada por springdoc y se mantiene
 * ademas versionado en el repositorio (Principio VI).</p>
 */
@Configuration
public class OpenApiConfig {

	/**
	 * @return la descripcion de la API publicada en Swagger UI
	 */
	@Bean
	public OpenAPI portfolioOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Portfolio API")
				.description("API de inversiones: portfolio, instrumentos, ordenes y transferencias de efectivo.")
				.version("1.0.0"));
	}

}
