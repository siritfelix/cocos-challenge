package ar.cocos.challenge.portfolio.orders.application;

import java.math.BigDecimal;
import java.util.Optional;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.LatestQuote;
import ar.cocos.challenge.portfolio.instruments.domain.MarketDataRepository;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import org.springframework.stereotype.Component;

/**
 * Resuelve el precio de ejecucion de una orden.
 *
 * <p>Para una orden a mercado, el precio es el ultimo cierre disponible del
 * instrumento; si no hay cotizacion utilizable, la orden se rechaza por regla
 * de negocio (spec FR-027, FR-028). Para una orden limite, el precio es el
 * limite informado por el usuario (spec FR-029).</p>
 */
@Component
public class OrderPricingPolicy {

	private final MarketDataRepository marketDataRepository;

	/**
	 * @param marketDataRepository repositorio de cotizaciones
	 */
	public OrderPricingPolicy(MarketDataRepository marketDataRepository) {
		this.marketDataRepository = marketDataRepository;
	}

	/**
	 * Resuelve el precio de una orden a mercado.
	 *
	 * @param instrument instrumento negociado
	 * @return el ultimo cierre disponible, o vacio si no hay cotizacion utilizable
	 */
	public Optional<BigDecimal> resolveMarketPrice(Instrument instrument) {
		return marketDataRepository.findLatestByInstrumentId(instrument.id())
				.filter(LatestQuote::isAvailable)
				.map(LatestQuote::close);
	}

	/**
	 * Resuelve el precio de una orden limite.
	 *
	 * @param limitPrice precio limite informado por el usuario
	 * @return el precio limite
	 */
	public BigDecimal resolveLimitPrice(BigDecimal limitPrice) {
		return limitPrice;
	}

	/**
	 * @param type tipo de ejecucion de la orden
	 * @return {@code true} si el tipo requiere resolver el precio de mercado
	 */
	public boolean requiresMarketPrice(OrderType type) {
		return type == OrderType.MARKET;
	}

}
