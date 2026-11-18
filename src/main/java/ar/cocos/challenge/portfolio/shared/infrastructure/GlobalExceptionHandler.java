package ar.cocos.challenge.portfolio.shared.infrastructure;

import java.time.LocalDateTime;
import java.util.List;

import ar.cocos.challenge.portfolio.shared.application.ClockProvider;
import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.ConflictException;
import ar.cocos.challenge.portfolio.shared.domain.DomainException;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import ar.cocos.challenge.portfolio.shared.domain.MissingAccountHeaderException;
import ar.cocos.challenge.portfolio.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Manejador central de los errores de la API.
 *
 * <p>Toda respuesta de error adopta la misma estructura (spec seccion 14) y
 * nunca expone trazas ni detalles internos (Principio VI, FR-091).</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private final ClockProvider clockProvider;

	/**
	 * @param clockProvider fuente de la hora UTC de las respuestas de error
	 */
	public GlobalExceptionHandler(ClockProvider clockProvider) {
		this.clockProvider = clockProvider;
	}

	/**
	 * Traduce un recurso inexistente, como un instrumento con ticker desconocido.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 404 Not Found}
	 */
	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce la ausencia de la cabecera de cuenta.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(MissingAccountHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingAccountHeader(MissingAccountHeaderException exception,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce la ausencia de una cabecera obligatoria declarada en el
	 * controller, como {@code X-AccountNumber} o {@code Idempotency-Key}.
	 *
	 * @param exception error de resolucion de la cabecera
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
	public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
			org.springframework.web.bind.MissingRequestHeaderException exception, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce una cuenta inexistente.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 404 Not Found}
	 */
	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException exception,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce un conflicto de estado o de idempotencia.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 409 Conflict}
	 */
	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce una solicitud invalida detectada por las reglas de negocio.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException exception,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce cualquier otro error de dominio como solicitud invalida.
	 *
	 * @param exception error de dominio
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(DomainException.class)
	public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
	}

	/**
	 * Traduce el fallo de la validacion declarativa del cuerpo de la solicitud.
	 *
	 * @param exception error de validacion de Spring
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request} con el detalle de los campos
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<String> details = exception.getBindingResult().getFieldErrors().stream()
				.map(this::describeFieldError)
				.toList();
		return build(HttpStatus.BAD_REQUEST, "Invalid request", request, details);
	}

	/**
	 * Traduce el fallo de la validacion de los parametros del metodo.
	 *
	 * @param exception error de validacion de Spring
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Invalid request", request, List.of());
	}

	/**
	 * Traduce el incumplimiento de una restriccion de Bean Validation.
	 *
	 * @param exception error de validacion
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
			HttpServletRequest request) {
		List<String> details = exception.getConstraintViolations().stream()
				.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
				.toList();
		return build(HttpStatus.BAD_REQUEST, "Invalid request", request, details);
	}

	/**
	 * Traduce un valor de parametro con tipo incorrecto, por ejemplo un
	 * {@code page} no numerico.
	 *
	 * @param exception error de conversion de Spring
	 * @param request solicitud que fallo
	 * @return respuesta {@code 400 Bad Request}
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + exception.getName(), request,
				List.of());
	}

	/**
	 * Traduce cualquier error inesperado sin filtrar detalles internos.
	 *
	 * @param exception error inesperado
	 * @param request solicitud que fallo
	 * @return respuesta {@code 500 Internal Server Error}
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, List.of());
	}

	private String describeFieldError(FieldError fieldError) {
		if (fieldError.getDefaultMessage() == null) {
			return fieldError.getField() + ": invalid value";
		}
		return fieldError.getField() + ": " + fieldError.getDefaultMessage();
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
			List<String> details) {
		LocalDateTime timestamp = clockProvider.now();
		ErrorResponse body = new ErrorResponse(timestamp, status.value(), status.getReasonPhrase(), message,
				request.getRequestURI(), details);
		return ResponseEntity.status(status).body(body);
	}

}
