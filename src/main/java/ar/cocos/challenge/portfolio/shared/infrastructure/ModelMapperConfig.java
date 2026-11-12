package ar.cocos.challenge.portfolio.shared.infrastructure;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del mapeador entre DTOs, objetos de dominio y entidades JPA.
 *
 * <p>El proyecto usa ModelMapper (Principio V); MapStruct esta prohibido por
 * la constitucion.</p>
 *
 * <p>La configuracion es <em>estricta</em> a proposito: un campo mal nombrado
 * en un DTO debe fallar de forma visible en los tests en lugar de quedar sin
 * mapear en silencio. Con estrategia laxa, un mapeo equivocado pasaria
 * inadvertido hasta las aserciones de la API.</p>
 *
 * <p>Limitaciones verificadas empíricamente de ModelMapper 3.2.8 que condicionan
 * el diseno del mapeo:</p>
 * <ul>
 *   <li>No puede instanciar <em>records</em> como destino: exige un constructor
 *       sin argumentos. Por eso los DTOs de salida son clases Java con getters
 *       y los records se reservan para el dominio.</li>
 *   <li>No aplana propiedades anidadas de forma implicita: un campo
 *       {@code ticker} en el destino no se rellena desde
 *       {@code order.instrument.ticker}. Los campos que provienen de una
 *       relacion se mapean de forma explicita con {@link org.modelmapper.PropertyMap}.</li>
 * </ul>
 */
@Configuration
public class ModelMapperConfig {

	/**
	 * @return el mapeador configurado con estrategia estricta
	 */
	@Bean
	public ModelMapper modelMapper() {
		ModelMapper modelMapper = new ModelMapper();
		modelMapper.getConfiguration()
				.setMatchingStrategy(MatchingStrategies.STRICT)
				.setFieldMatchingEnabled(false)
				.setSkipNullEnabled(false)
				.setAmbiguityIgnored(false)
				.setImplicitMappingEnabled(true)
				.setPreferNestedProperties(true);
		return modelMapper;
	}

}
