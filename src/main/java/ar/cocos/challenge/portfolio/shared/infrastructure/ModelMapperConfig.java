package ar.cocos.challenge.portfolio.shared.infrastructure;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del mapeador entre DTOs, objetos de dominio y entidades JPA.
 *
 * <p>El proyecto usa ModelMapper (Principio V); MapStruct esta prohibido por
 * la constitucion. La estrategia de coincidencia es estricta para que un
 * campo mal nombrado en un DTO falle de forma visible en los tests en lugar
 * de quedar sin mapear en silencio.</p>
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
				.setAmbiguityIgnored(false);
		return modelMapper;
	}

}
