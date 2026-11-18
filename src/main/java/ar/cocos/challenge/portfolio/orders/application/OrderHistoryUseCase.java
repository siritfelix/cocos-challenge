package ar.cocos.challenge.portfolio.orders.application;

import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.users.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**s
 * Caso de uso de consulta del historial de ordenes de una cuenta.
 *
 * <p>El historial incluye todos los estados: {@code NEW}, {@code FILLED},
 * {@code REJECTED} y {@code CANCELLED} (spec FR-080), y se ordena de la orden
 * mas reciente a la mas antigua.</p>
 *
 * <p>Los filtros se aplican SIEMPRE dentro del conjunto de ordenes de la cuenta
 * identificada (spec FR-087).</p>
 */
@Service
public class OrderHistoryUseCase {

	private final OrderRepository orderRepository;

	/**
	 * @param orderRepository repositorio de ordenes
	 */
	public OrderHistoryUseCase(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	/**
	 * Devuelve una pagina del historial de ordenes de la cuenta.
	 *
	 * @param account contexto de la cuenta
	 * @param filter filtros opcionales a aplicar
	 * @param pageable paginacion solicitada
	 * @return la pagina de ordenes
	 */
	@Transactional(readOnly = true)
	public Page<Order> query(Account account, OrderFilter filter, Pageable pageable) {
		return orderRepository.findHistory(account.id(),
				filter.statusFilter().orElse(null),
				filter.sideFilter().orElse(null),
				filter.tickerFilter().orElse(null),
				pageable);
	}

}

