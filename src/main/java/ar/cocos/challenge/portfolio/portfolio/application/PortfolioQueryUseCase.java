package ar.cocos.challenge.portfolio.portfolio.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentRepository;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import ar.cocos.challenge.portfolio.portfolio.domain.CashSummary;
import ar.cocos.challenge.portfolio.portfolio.domain.CostBasisCalculator;
import ar.cocos.challenge.portfolio.portfolio.domain.ExecutedTrade;
import ar.cocos.challenge.portfolio.portfolio.domain.InstrumentHolding;
import ar.cocos.challenge.portfolio.portfolio.domain.PerformanceCalculator;
import ar.cocos.challenge.portfolio.portfolio.domain.Portfolio;
import ar.cocos.challenge.portfolio.portfolio.domain.Position;
import ar.cocos.challenge.portfolio.users.domain.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de consulta del portfolio de una cuenta.
 *
 * <p>El estado patrimonial se deriva integramente de las ordenes registradas:
 * no hay tablas de saldos ni de posiciones (spec D-047). El efectivo proviene
 * de agregados SQL y el costo promedio de cada posicion requiere un fold
 * ordenado sobre las ordenes ejecutadas (research R-008, R-009).</p>
 *
 * <p>La consulta es de solo lectura: no toma el bloqueo pesimista de la cuenta,
 * que solo es necesario para las operaciones que modifican recursos
 * (research R-007).</p>
 */
@Service
public class PortfolioQueryUseCase {

	private final ExecutedOrdersPort executedOrdersPort;
	private final InstrumentRepository instrumentRepository;
	private final MarketDataRepository marketDataRepository;
	private final CostBasisCalculator costBasisCalculator;
	private final PerformanceCalculator performanceCalculator;

	/**
	 * @param executedOrdersPort puerto de lectura de ordenes
	 * @param instrumentRepository repositorio de instrumentos
	 * @param marketDataRepository repositorio de cotizaciones
	 * @param costBasisCalculator calculadora del costo promedio ponderado
	 * @param performanceCalculator calculadora de rendimientos
	 */
	public PortfolioQueryUseCase(ExecutedOrdersPort executedOrdersPort, InstrumentRepository instrumentRepository,
			MarketDataRepository marketDataRepository, CostBasisCalculator costBasisCalculator,
			PerformanceCalculator performanceCalculator) {
		this.executedOrdersPort = executedOrdersPort;
		this.instrumentRepository = instrumentRepository;
		this.marketDataRepository = marketDataRepository;
		this.costBasisCalculator = costBasisCalculator;
		this.performanceCalculator = performanceCalculator;
	}

	/**
	 * Devuelve el estado patrimonial de la cuenta.
	 *
	 * @param account contexto de la cuenta
	 * @return el portfolio de la cuenta
	 */
	@Transactional(readOnly = true)
	public Portfolio query(Account account) {
		CashSummary cash = executedOrdersPort.cashSummary(account);
		List<Position> positions = buildPositions(account);
		BigDecimal totalValue = cash.totalCash().add(sumMarketValue(positions));
		return new Portfolio(cash.totalCash(), cash.availableCash(), cash.reservedCash(), totalValue, positions);
	}

	private List<Position> buildPositions(Account account) {
		List<Position> positions = new ArrayList<>();

		List<InstrumentHolding> instrumentHoldings = executedOrdersPort.holdings(account);

		List<Long> ids = instrumentHoldings.stream().map(InstrumentHolding::instrumentId).toList();

		Map<Long, Instrument> instruments = instrumentRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Instrument::id, i -> i));

		Map<Long, LatestQuote> quotes = marketDataRepository.findLatestByInstrumentIds(ids);

		Map<Long, List<ExecutedTrade>> trades = executedOrdersPort.executedTradesByAccount(account);

		for (InstrumentHolding holding : instrumentHoldings) {
			Instrument instrument = instruments.getOrDefault(holding.instrumentId(), null);
			if (instrument == null) {
				continue;
			}
			positions.add(buildPosition(instrument, quotes.getOrDefault(instrument.id(), null),
					trades.getOrDefault(instrument.id(), List.of()), holding));
		}

		return positions;
	}

	private Position buildPosition(Instrument instrument, LatestQuote quote, List<ExecutedTrade> executedTrades,
			InstrumentHolding holding) {
		BigDecimal totalQuantity = holding.totalQuantity();

		BigDecimal currentPrice = quote == null ? null : quote.close();

		BigDecimal marketValue = currentPrice == null ? BigDecimal.ZERO : totalQuantity.multiply(currentPrice);

		BigDecimal averageCost = costBasisCalculator.averageCost(executedTrades);

		BigDecimal totalPerformance = performanceCalculator.totalPerformance(marketValue, averageCost, totalQuantity);
		BigDecimal dailyPerformance = performanceCalculator.dailyPerformance(quote);

		return new Position(instrument.ticker(), totalQuantity, holding.availableQuantity(),
				holding.reservedQuantity(), currentPrice, marketValue, averageCost, totalPerformance,
				dailyPerformance);
	}

	private BigDecimal sumMarketValue(List<Position> positions) {
		return positions.stream()
				.map(Position::marketValue)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}
