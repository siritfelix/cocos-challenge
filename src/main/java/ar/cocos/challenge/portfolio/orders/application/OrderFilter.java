package ar.cocos.challenge.portfolio.orders.application;

import java.util.Locale;
import java.util.Optional;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;

/**
 * Filtros opcionales del historial de ordenes.
 *
 * <p>Los tres filtros son independientes y combinables entre si (spec FR-084).
 * Todos se aplican SIEMPRE dentro del conjunto de ordenes de la cuenta
 * identificada (spec FR-087).</p>
 *
 * <p>Los valores admitidos son los del dominio: {@code NEW}, {@code FILLED},
 * {@code REJECTED} y {@code CANCELLED} para el estado; {@code BUY},
 * {@code SELL}, {@code CASH_IN} y {@code CASH_OUT} para el lado. Un valor
 * fuera de esos conjuntos es un error de solicitud (spec FR-085).</p>
 */
public record OrderFilter(OrderStatus status, OrderSide side, String ticker) {

	/**
	 * Construye el filtro a partir de los parametros crudos de la solicitud.
	 *
	 * @param status estado por el que filtrar, o {@code null} para no filtrar
	 * @param side lado por el que filtrar, o {@code null} para no filtrar
	 * @param ticker ticker exacto por el que filtrar, o {@code null} para no
	 *        filtrar
	 * @return el filtro normalizado
	 * @throws InvalidRequestException si un valor esta fuera de los conjuntos
	 *         admitidos
	 */
	public static OrderFilter of(String status, String side, String ticker) {
		return new OrderFilter(parseStatus(status), parseSide(side), parseTicker(ticker));
	}

	/**
	 * @return el filtro sin ningun criterio aplicado
	 */
	public static OrderFilter empty() {
		return new OrderFilter(null, null, null);
	}

	private static OrderStatus parseStatus(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new InvalidRequestException("Invalid value for parameter 'status': " + value);
		}
	}

	private static OrderSide parseSide(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OrderSide.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new InvalidRequestException("Invalid value for parameter 'side': " + value);
		}
	}

	private static String parseTicker(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim().toUpperCase(Locale.ROOT);
	}

	/**
	 * @return {@code true} si no hay ningun criterio de filtrado
	 */
	public boolean isEmpty() {
		return status == null && side == null && ticker == null;
	}

	/**
	 * @return el estado por el que se filtra, si existe
	 */
	public Optional<OrderStatus> statusFilter() {
		return Optional.ofNullable(status);
	}

	/**
	 * @return el lado por el que se filtra, si existe
	 */
	public Optional<OrderSide> sideFilter() {
		return Optional.ofNullable(side);
	}

	/**
	 * @return el ticker exacto por el que se filtra, si existe
	 */
	public Optional<String> tickerFilter() {
		return Optional.ofNullable(ticker);
	}

}
