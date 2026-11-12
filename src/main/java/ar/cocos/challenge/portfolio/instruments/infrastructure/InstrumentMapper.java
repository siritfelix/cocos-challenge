package ar.cocos.challenge.portfolio.instruments.infrastructure;

import java.util.List;

import ar.cocos.challenge.portfolio.instruments.application.InstrumentResponse;
import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.QuotedInstrument;
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
	 * Convierte un instrumento con su cotizacion en el DTO de respuesta.
	 *
	 * <p>Reglas de mapeo del precio (spec 002, data-model.md): el
	 * {@code price} se informa solo cuando la cotizacion es utilizable segun
	 * {@code LatestQuote.isAvailable()}; el {@code previousClose} solo cuando
	 * esa cotizacion declara un cierre anterior utilizable segun
	 * {@code hasPreviousClose()}. Un instrumento sin cotizacion no expone
	 * ninguno de los dos campos.</p>
	 *
	 * @param quoted instrumento del dominio con su cotizacion mas reciente
	 * @return el DTO de respuesta
	 */
	public InstrumentResponse toResponse(QuotedInstrument quoted) {
		InstrumentResponse response = modelMapper.map(quoted.instrument(), InstrumentResponse.class);
		LatestQuote quote = quoted.quote();
		if (quote != null) {
			if (quote.isAvailable()) {
				response.setPrice(quote.close());
			}
			if (quote.hasPreviousClose()) {
				response.setPreviousClose(quote.previousClose());
			}
		}
		return response;
	}

	/**
	 * Convierte una lista de instrumentos con sus cotizaciones en los DTOs de
	 * respuesta.
	 *
	 * @param quotedInstruments instrumentos del dominio con sus cotizaciones
	 * @return los DTOs de respuesta
	 */
	public List<InstrumentResponse> toResponses(List<QuotedInstrument> quotedInstruments) {
		return quotedInstruments.stream().map(this::toResponse).toList();
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
