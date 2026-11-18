package ar.cocos.challenge.portfolio.orders.application;

import ar.cocos.challenge.portfolio.orders.domain.InvalidOrderStateException;
import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import ar.cocos.challenge.portfolio.shared.domain.AccountNotFoundException;
import ar.cocos.challenge.portfolio.shared.domain.ResourceNotFoundException;
import ar.cocos.challenge.portfolio.shared.infrastructure.LockTimeoutInitializer;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de cancelacion de una orden pendiente.
 *
 * <p>Solo las ordenes en estado {@code NEW} pueden cancelarse (spec FR-062).
 * Al cancelar, los recursos que la orden tenian reservados se liberan: la
 * liberacion es implicita, porque las reservas se derivan de las ordenes en
 * estado {@code NEW} (spec FR-063).</p>
 *
 * <p>Una orden inexistente o perteneciente a otra cuenta produce el mismocle
 * {@code 404}, de modo que no se revela la existencia de ordenes ajenas
 * (spec FR-065).</p>
 */
@Service
public class CancelOrderUseCase {

	private final AccountRepository accountRepository;
	private final OrderRepository orderRepository;
	private final LockTimeoutInitializer lockTimeoutInitializer;

	/**
	 * @param accountRepository repositorio de cuentas
	 * @param orderRepository repositorio de ordenes
	 * @param lockTimeoutInitializer acotador de la espera por bloqueos
	 */
	public CancelOrderUseCase(AccountRepository accountRepository, OrderRepository orderRepository,
			LockTimeoutInitializer lockTimeoutInitializer) {
		this.accountRepository = accountRepository;
		this.orderRepository = orderRepository;
		this.lockTimeoutInitializer = lockTimeoutInitializer;
	}

	/**
	 * Cancela la orden indicada.
	 *
	 * @param account contexto de la cuenta
	 * @param orderId identificador de la orden a cancelar
	 * @return la orden cancelada
	 * @throws AccountNotFoundException si la cuenta no existe
	 * @throws ResourceNotFoundException si la orden no existe o no pertenece a la
	 *         cuenta
	 * @throws InvalidOrderStateException si la orden no esta en estado
	 *         {@code NEW}
	 */
	@Transactional
	public Order cancel(Account account, Long orderId) {
		// El limite de espera por bloqueos se emite como PRIMERA sentencia de la
		// transaccion, antes de cualquier lectura (research R-006).
		lockTimeoutInitializer.apply();

		// El lock de la cuenta serializa las cancelaciones concurrentes de sus
		// ordenes, igual que las operaciones que consumen recursos
		// (spec FR-066).
		Account resolvedAccount = accountRepository.findByAccountNumberForUpdate(account.accountNumber())
				.orElseThrow(() -> new AccountNotFoundException(account.accountNumber()));

		Order order = orderRepository.findByIdAndAccountId(orderId, resolvedAccount.id())
				.orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

		if (order.getStatus() != OrderStatus.NEW) {
			throw InvalidOrderStateException.notCancellable(orderId, order.getStatus());
		}

		return orderRepository.update(order.cancel());
	}

}
