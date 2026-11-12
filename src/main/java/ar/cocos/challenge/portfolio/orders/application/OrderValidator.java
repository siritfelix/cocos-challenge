package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * Valida las reglas del request que dependen del contexto de la operacion.
 *
 * <p>Un fallo aqui es un error de solicitud: no se persiste ninguna orden y la
 * respuesta es {@code 400 Bad Request} (spec FR-026, FR-068).</p>
 */
@Component
public class OrderValidator {

	/**
	 * Valida la forma del request segun el lado y el tipo de la operacion.
	 *
	 * @param request solicitud de creacion de orden
	 * @throws InvalidRequestException si la solicitud infringe alguna regla
	 */
	public void validate(CreateOrderRequest request) {
		validateSizeAndAmount(request);
		validatePrice(request);
	}

	private void validateSizeAndAmount(CreateOrderRequest request) {
		boolean hasSize = request.getSize() != null;
		boolean hasAmount = request.getAmount() != null;

		if (hasSize && hasAmount) {
			throw new InvalidRequestException("Only one of 'size' or 'amount' must be provided");
		}
		if (!hasSize && !hasAmount) {
			throw new InvalidRequestException("Either 'size' or 'amount' must be provided");
		}
		if (request.getSide().isTransfer() && hasAmount) {
			throw new InvalidRequestException("Cash transfers must use 'size' to express the amount");
		}
	}

	private void validatePrice(CreateOrderRequest request) {
		OrderSide side = request.getSide();
		OrderType type = request.getType();

		if (side.isTransfer()) {
			if (type != OrderType.MARKET) {
				throw new InvalidRequestException("Cash transfers must be of type MARKET");
			}
			if (request.getPrice() != null) {
				throw new InvalidRequestException("Cash transfers must not provide a price");
			}
			return;
		}

		if (type == OrderType.MARKET) {
			if (request.getPrice() != null) {
				throw new InvalidRequestException("MARKET orders must not provide a price");
			}
			return;
		}

		if (request.getPrice() == null) {
			throw new InvalidRequestException("LIMIT orders require a price");
		}
		if (request.getPrice().signum() <= 0) {
			throw new InvalidRequestException("LIMIT orders require a price greater than zero");
		}
	}

	/**
	 * Verifica que la cantidad de acciones de una compra o venta sea entera.
	 *
	 * <p>No se admiten fracciones de acciones (spec FR-017).</p>
	 *
	 * @param quantity cantidad de acciones
	 * @throws InvalidRequestException si la cantidad tiene parte fraccionaria
	 */
	public void validateWholeQuantity(BigDecimal quantity) {
		if (quantity.stripTrailingZeros().scale() > 0) {
			throw new InvalidRequestException("Share quantities must be whole numbers");
		}
	}

	/**
	 * Verifica que el instrumento admita la operacion solicitada.
	 *
	 * <p>Las compras y las ventas solo pueden hacerse sobre instrumentos de
	 * tipo acciones, y las transferencias solo sobre el instrumento de efectivo
	 * {@code ARS} (spec FR-021, FR-022, D-025).</p>
	 *
	 * @param request solicitud de creacion de orden
	 * @param instrument instrumento solicitado
	 * @throws InvalidRequestException si el instrumento no admite la operacion
	 */
	public void validateInstrument(CreateOrderRequest request, Instrument instrument) {
		if (request.getSide().isTransfer()) {
			if (!instrument.isCash()) {
				throw new InvalidRequestException(
						"Cash transfers must use the '" + Instrument.CASH_TICKER + "' instrument");
			}
			return;
		}
		if (instrument.isCash()) {
			throw new InvalidRequestException("The '" + Instrument.CASH_TICKER
					+ "' instrument cannot be traded; use a cash transfer instead");
		}
		if (!instrument.isTradeable()) {
			throw new InvalidRequestException("Instrument " + instrument.ticker() + " is not tradable");
		}
	}

}
