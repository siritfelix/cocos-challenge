package ar.cocos.challenge.portfolio.portfolio.infrastructure;

import java.math.BigDecimal;
import java.util.List;

import ar.cocos.challenge.portfolio.orders.domain.Order;
import ar.cocos.challenge.portfolio.orders.domain.OrderRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.portfolio.application.ExecutedOrdersPort;
import ar.cocos.challenge.portfolio.portfolio.domain.CashSummary;
import ar.cocos.challenge.portfolio.portfolio.domain.ExecutedTrade;
import ar.cocos.challenge.portfolio.portfolio.domain.InstrumentHolding;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link ExecutedOrdersPort} sobre el repositorio de
 * ordenes.
 *
 * <p>Es el unico punto por el que el contexto de portfolio obtiene datos del
 * ledger de ordenes; el resto de la logica de portfolio no depende de la
 * infraestructura (Principio V).</p>
 */
@Component
public class OrdersReadAdapter implements ExecutedOrdersPort {

	private final OrderRepository orderRepository;

	/**
	 * @param orderRepository repositorio de ordenes
	 */
	public OrdersReadAdapter(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public CashSummary cashSummary(Long accountId) {
		BigDecimal totalCash = orderRepository.sumFilledCash(accountId);
		BigDecimal reservedCash = orderRepository.sumReservedCash(accountId);
		return new CashSummary(totalCash, reservedCash, totalCash.subtract(reservedCash));
	}

	@Override
	@Transactional(readOnly = true)
	public List<InstrumentHolding> holdings(Long accountId) {
		return orderRepository.findInstrumentsWithPosition(accountId).stream()
				.map(instrumentId -> new InstrumentHolding(instrumentId,
						orderRepository.sumFilledShares(accountId, instrumentId),
						orderRepository.sumReservedShares(accountId, instrumentId)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ExecutedTrade> executedTrades(Long accountId, Long instrumentId) {
		return orderRepository.findExecutedTradesInChronologicalOrder(accountId, instrumentId).stream()
				.map(this::toExecutedTrade)
				.toList();
	}

	private ExecutedTrade toExecutedTrade(Order order) {
		ExecutedTrade.TradeSide side = order.getSide() == OrderSide.SELL
				? ExecutedTrade.TradeSide.SELL
				: ExecutedTrade.TradeSide.BUY;
		return new ExecutedTrade(side, order.getSize(), order.getPrice());
	}

}
