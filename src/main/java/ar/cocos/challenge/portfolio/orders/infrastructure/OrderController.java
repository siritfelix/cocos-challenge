package ar.cocos.challenge.portfolio.orders.infrastructure;

import ar.cocos.challenge.portfolio.orders.application.CreateOrderRequest;
import ar.cocos.challenge.portfolio.orders.application.CreateOrderUseCase;
import ar.cocos.challenge.portfolio.orders.application.OrderResponse;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de las ordenes.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	/**
	 * Nombre de la cabecera que transporta el numero de cuenta.
	 */
	private static final String ACCOUNT_HEADER = "X-AccountNumber";

	/**
	 * Nombre de la cabecera que transporta la clave de idempotencia.
	 */
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final CreateOrderUseCase createOrderUseCase;
	private final OrderMapper orderMapper;

	/**
	 * @param createOrderUseCase caso de uso de creacion de ordenes
	 * @param orderMapper mapeador entre dominio y DTOs
	 */
	public OrderController(CreateOrderUseCase createOrderUseCase, OrderMapper orderMapper) {
		this.createOrderUseCase = createOrderUseCase;
		this.orderMapper = orderMapper;
	}

	/**
	 * Crea una orden o una transferencia de efectivo.
	 *
	 * <p>Responde {@code 201 Created} tanto si la orden queda ejecutada, pendiente
	 * o rechazada por regla de negocio (spec FR-048). Los errores de validacion
	 * del request no persisten ninguna orden y se traducen a
	 * {@code 400 Bad Request}.</p>
	 *
	 * @param accountNumber numero de cuenta
	 * @param idempotencyKey clave de idempotencia de la solicitud
	 * @param request solicitud de creacion
	 * @return la orden creada
	 */
	@PostMapping
	public ResponseEntity<OrderResponse> createOrder(
			@RequestHeader(value = ACCOUNT_HEADER, required = false) String accountNumber,
			@RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new InvalidRequestException("Missing required header 'Idempotency-Key'");
		}
		Order order = createOrderUseCase.create(accountNumber, idempotencyKey, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
	}

}
