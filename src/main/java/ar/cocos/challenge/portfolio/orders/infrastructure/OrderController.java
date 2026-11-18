package ar.cocos.challenge.portfolio.orders.infrastructure;

import ar.cocos.challenge.portfolio.orders.application.CancelOrderUseCase;
import ar.cocos.challenge.portfolio.orders.application.CreateOrderRequest;
import ar.cocos.challenge.portfolio.orders.application.CreateOrderUseCase;
import ar.cocos.challenge.portfolio.orders.application.OrderFilter;
import ar.cocos.challenge.portfolio.orders.application.OrderHistoryUseCase;
import ar.cocos.challenge.portfolio.orders.application.OrderPageResponse;
import ar.cocos.challenge.portfolio.orders.application.OrderResponse;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import ar.cocos.challenge.portfolio.shared.infrastructure.ErrorResponse;
import ar.cocos.challenge.portfolio.shared.infrastructure.PageRequestValidator;
import ar.cocos.challenge.portfolio.users.domain.Account;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de las ordenes.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

	/**
	 * Nombre de la cabecera que transporta la clave de idempotencia.
	 */
	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final CreateOrderUseCase createOrderUseCase;
	private final CancelOrderUseCase cancelOrderUseCase;
	private final OrderHistoryUseCase orderHistoryUseCase;
	private final OrderMapper orderMapper;
	private final PageRequestValidator pageRequestValidator;

	/**
	 * @param createOrderUseCase caso de uso de creacion de ordenes
	 * @param cancelOrderUseCase caso de uso de cancelacion de ordenes
	 * @param orderHistoryUseCase caso de uso de consulta del historial
	 * @param orderMapper mapeador entre dominio y DTOs
	 * @param pageRequestValidator validador de la paginacion
	 */
	public OrderController(CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase,
			OrderHistoryUseCase orderHistoryUseCase, OrderMapper orderMapper, PageRequestValidator pageRequestValidator) {
		this.createOrderUseCase = createOrderUseCase;
		this.cancelOrderUseCase = cancelOrderUseCase;
		this.orderHistoryUseCase = orderHistoryUseCase;
		this.orderMapper = orderMapper;
		this.pageRequestValidator = pageRequestValidator;
	}

	/**
	 * Crea una orden o una transferencia de efectivo.
	 *
	 * <p>Responde {@code 201 Created} tanto si la orden queda ejecutada, pendiente
	 * o rechazada por regla de negocio (spec FR-048). Los errores de validacion
	 * del request no persisten ninguna orden y se traducen a
	 * {@code 400 Bad Request}.</p>
	 *
	 * @param account contexto de la cuenta
	 * @param idempotencyKey clave de idempotencia de la solicitud
	 * @param request solicitud de creacion
	 * @return la orden creada
	 */
	@PostMapping
	@Parameters({
			@Parameter(name = "X-AccountNumber", in = ParameterIn.HEADER, required = true,
					description = "Numero de cuenta", schema = @Schema(type = "string"))})
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "Solicitud invalida o error de validacion",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cuenta o instrumento inexistente",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Conflicto de estado o de idempotencia",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
	public ResponseEntity<OrderResponse> createOrder(
			Account account,
			@RequestHeader(value = IDEMPOTENCY_HEADER, required = true) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new InvalidRequestException("Missing required header 'Idempotency-Key'");
		}
		Order order = createOrderUseCase.create(account, idempotencyKey, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toResponse(order));
	}

	/**
	 * Devuelve el historial de ordenes de la cuenta.
	 *
	 * <p>Incluye todos los estados y se ordena de la orden mas reciente a la mas
	 * antigua (spec FR-080). Admite paginacion y filtros combinables por
	 * estado, lado e instrumento (spec FR-081, FR-084).</p>
	 *
	 * @param account contexto de la cuenta
	 * @param page pagina solicitada, base 0
	 * @param size cantidad de elementos por pagina
	 * @param status estado por el que filtrar, opcional
	 * @param side lado por el que filtrar, opcional
	 * @param ticker ticker exacto por el que filtrar, opcional
	 * @return la pagina de ordenes de la cuenta
	 */
	@GetMapping
	@Parameters({
			@Parameter(name = "X-AccountNumber", in = ParameterIn.HEADER, required = true,
					description = "Numero de cuenta", schema = @Schema(type = "string"))})
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "Solicitud invalida o error de validacion",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cuenta inexistente",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
	public ResponseEntity<OrderPageResponse> getOrders(
			Account account,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String side,
			@RequestParam(required = false) String ticker) {
		pageRequestValidator.validate(page, size);
		OrderFilter filter = OrderFilter.of(status, side, ticker);
		Page<Order> orders = orderHistoryUseCase.query(account, filter, PageRequest.of(page, size));
		return ResponseEntity.ok(orderMapper.toPageResponse(orders));
	}

	/**
	 * Cancela una orden pendiente.
	 *
	 * <p>Solo las ordenes en estado {@code NEW} son cancelables; las demas
	 * responden {@code 409 Conflict}. Una orden inexistente o de otra cuenta
	 * responde {@code 404 Not Found} sin revelar su existencia (spec §9).</p>
	 *
	 * @param account contexto de la cuenta
	 * @param orderId identificador de la orden a cancelar
	 * @return la orden cancelada
	 */
	@DeleteMapping("/{orderId}")
	@Parameters({
			@Parameter(name = "X-AccountNumber", in = ParameterIn.HEADER, required = true,
					description = "Numero de cuenta", schema = @Schema(type = "string"))})
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "Solicitud invalida o error de validacion",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Orden inexistente o de otra cuenta",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado NEW",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
	public ResponseEntity<OrderResponse> cancelOrder(
			Account account,
			@PathVariable("orderId") Long orderId) {
		Order order = cancelOrderUseCase.cancel(account, orderId);
		return ResponseEntity.ok(orderMapper.toResponse(order));
	}

}
