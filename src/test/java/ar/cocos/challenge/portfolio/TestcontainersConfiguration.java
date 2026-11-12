package ar.cocos.challenge.portfolio;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provee el contenedor PostgreSQL usado por los tests de integracion.
 *
 * <p>La anotacion {@code @ServiceConnection} conecta el contenedor con el
 * datasource, la conexion de Liquibase y la de Testcontainers de Spring Boot,
 * de modo que no hace falta declarar credenciales en ningun archivo de
 * configuracion (Principio VII).</p>
 *
 * <p>El contenedor se declara como bean y no como campo {@code @Container} estatico
 * para que su ciclo de vida quede ligado al del contexto de Spring: la documentacion
 * oficial recomienda esta forma cuando el contexto se cachea entre clases de test.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/**
	 * Imagen de PostgreSQL usada por los tests.
	 */
	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

	/**
	 * Contenedor PostgreSQL compartido por los tests de integracion.
	 *
	 * @return el contenedor con la anotacion que enlaza sus parametros de
	 *         conexion con el contexto de Spring
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}
