package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import org.springframework.stereotype.Component;

/**
 * Calcula los recursos disponibles de una cuenta a partir de las ordenes
 * registradas.
 *
 * <p>El efectivo disponible descuenta las reservas de las compras limite
 * pendientes, y las acciones disponibles descuentan las reservas de las ventas
 * limite pendientes (spec FR-033, FR-034, FR-037).</p>
 */
@Component
public class ResourceCalculator {

	private final OrderRepository orderRepository;

	/**
	 * @param orderRepository repositorio de ordenes
	 */
	public ResourceCalculator(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	/**
	 * Calcula los recursos de efectivo de la cuenta.
	 *
	 * @param accountId identificador de la cuenta
	 * @return el efectivo total, reservado y disponible
	 */
	public AccountResources calculateCash(Long accountId) {
		BigDecimal totalCash = orderRepository.sumFilledCash(accountId);
		BigDecimal reservedCash = orderRepository.sumReservedCash(accountId);
		BigDecimal availableCash = totalCash.subtract(reservedCash);
		return new AccountResources(totalCash, reservedCash, availableCash, BigDecimal.ZERO);
	}

	/**
	 * Calcula los recursos de acciones de un instrumento para la cuenta.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrument instrumento evaluado
	 * @return las acciones disponibles
	 */
	public BigDecimal calculateAvailableShares(Long accountId, Instrument instrument) {
		BigDecimal filledShares = orderRepository.sumFilledShares(accountId, instrument.id());
		BigDecimal reservedShares = orderRepository.sumReservedShares(accountId, instrument.id());
		return filledShares.subtract(reservedShares);
	}

}
