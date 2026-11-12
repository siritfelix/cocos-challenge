package ar.cocos.challenge.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Verifica que el contexto de Spring arranca correctamente, con el esquema
 * aplicado por Liquibase y el datasource provisto por Testcontainers.
 *
 * <p>En Spring Boot 4 {@code @SpringBootTest} ya no provee MockMvc por si solo:
 * hay que declararlo explicitamente con {@code @AutoConfigureMockMvc}, que vive
 * en el paquete {@code org.springframework.boot.webmvc.test.autoconfigure}
 * (research R-012).</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioApplicationTests {

	@Test
	void contextLoads() {
	}

}
