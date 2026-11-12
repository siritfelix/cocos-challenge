package ar.cocos.challenge.portfolio.orders.infrastructure;

import ar.cocos.challenge.portfolio.orders.application.OrderResponse;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/**
 * Mapeador entre las ordenes del dominio y los DTOs de la API.
 *
 * <p>El mapeo se declara con un {@link org.modelmapper.Converter} explicito
 * porque el mapeo por propiedades no puede resolver los tipos del dominio:</p>
 * <ul>
 *   <li>{@code Instrument} y {@code RejectionReason} son tipos finales (records
 *       y enums) y ModelMapper 3.2.8 rechaza mapearlos, al intentar
 *       aplanar {@code order.instrument.ticker} hacia el destino.</li>
 *   <li>El motivo de rechazo debe exponerse como texto legible, no como el
 *       nombre del enum.</li>
 * </ul>
 *
 * <p>La constitucion exige ModelMapper (y prohibe MapStruct), y permite que
 * los mapeos no triviales se configuren de forma explicita.</p>
 */
@Component
public class OrderMapper {

	private final ModelMapper modelMapper;

	/**
	 * @param modelMapper mapeador configurado por la aplicacion
	 */
	public OrderMapper(ModelMapper modelMapper) {
		this.modelMapper = modelMapper;
		this.modelMapper.createTypeMap(Order.class, OrderResponse.class)
				.setConverter(context -> buildResponse(context.getSource()));
	}

	/**
	 * Convierte una orden del dominio en su DTO de respuesta.
	 *
	 * @param order orden del dominio
	 * @return el DTO de respuesta
	 */
	public OrderResponse toResponse(Order order) {
		return modelMapper.map(order, OrderResponse.class);
	}

	private OrderResponse buildResponse(Order order) {
		OrderResponse response = new OrderResponse();
		response.setId(order.getId());
		response.setTicker(order.getInstrument().ticker());
		response.setSide(order.getSide());
		response.setType(order.getType());
		response.setSize(order.getSize());
		response.setPrice(order.getPrice());
		response.setStatus(order.getStatus());
		response.setRejectionReason(
				order.getRejectionReason() == null ? null : order.getRejectionReason().message());
		response.setDatetime(order.getDatetime());
		return response;
	}

}
