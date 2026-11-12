package ar.cocos.challenge.portfolio.shared.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion web de la aplicacion.
 *
 * <p>La serializacion de fechas y enums se controla por las propiedades de
 * Jackson en {@code application.yaml}, donde se desactivan los defaults de
 * Jackson 3 que alterarian el contrato HTTP (research R-011, R-014).</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

}
