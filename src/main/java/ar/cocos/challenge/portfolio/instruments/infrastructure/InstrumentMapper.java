package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;

import ar.cocos.challenge.portfolio.instruments.application.InstrumentResponse;
import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/**
 * Mapeador entre los instrumentos del dominio y los DTOs de la API.
 *
 * <p>El mapeo se declara con un {@link org.modelmapper.Converter} explicito
 * porque ModelMapper 3.2.8 no puede instanciar records ni mapear tipos
 * finales, y {@link Instrument} es un record (research documentado en
 * {@code ModelMapperConfig}).</p>
 */
@Component
public class InstrumentMapper {

	private final ModelMapper modelMapper;

	/**
	 * @param modelMapper mapeador configurado por la aplicacion
	 */
	public InstrumentMapper(ModelMapper modelMapper) {
		this.modelMapper = modelMapper;
		this.modelMapper.createTypeMap(Instrument.class, InstrumentResponse.class)
				.setConverter(context -> buildResponse(context.getSource()));
	}

	/**
	 * Convierte un instrumento del dominio en su DTO de respuesta.
	 *
	 * @param instrument instrumento del dominio
	 * @return el DTO de respuesta
	 */
	public InstrumentResponse toResponse(Instrument instrument) {
		return modelMapper.map(instrument, InstrumentResponse.class);
	}

	/**
	 * Convierte una lista de instrumentos en sus DTOs de respuesta.
	 *
	 * @param instruments instrumentos del dominio
	 * @return los DTOs de respuesta
	 */
	public List<InstrumentResponse> toResponses(List<Instrument> instruments) {
		return instruments.stream().map(this::toResponse).toList();
	}

	private InstrumentResponse buildResponse(Instrument instrument) {
		InstrumentResponse response = new InstrumentResponse();
		response.setId(instrument.id());
		response.setTicker(instrument.ticker());
		response.setName(instrument.name());
		response.setType(instrument.type());
		return response;
	}

}
