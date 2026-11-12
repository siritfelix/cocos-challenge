package ar.cocos.challenge.portfolio.portfolio.application;

import java.math.BigDecimal;

/**
 * Posicion de un instrumento en la respuesta de la API (spec FR-069).
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo.</p>
 */
public class PositionResponse {

	private String ticker;
	private BigDecimal totalQuantity;
	private BigDecimal availableQuantity;
	private BigDecimal reservedQuantity;
	private BigDecimal currentPrice;
	private BigDecimal marketValue;
	private BigDecimal averageCost;
	private BigDecimal totalPerformance;
	private BigDecimal dailyPerformance;

	/**
	 * @return el simbolo del instrumento
	 */
	public String getTicker() {
		return ticker;
	}

	/**
	 * @param ticker simbolo del instrumento
	 */
	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	/**
	 * @return la cantidad total de acciones
	 */
	public BigDecimal getTotalQuantity() {
		return totalQuantity;
	}

	/**
	 * @param totalQuantity cantidad total de acciones
	 */
	public void setTotalQuantity(BigDecimal totalQuantity) {
		this.totalQuantity = totalQuantity;
	}

	/**
	 * @return la cantidad disponible
	 */
	public BigDecimal getAvailableQuantity() {
		return availableQuantity;
	}

	/**
	 * @param availableQuantity cantidad disponible
	 */
	public void setAvailableQuantity(BigDecimal availableQuantity) {
		this.availableQuantity = availableQuantity;
	}

	/**
	 * @return la cantidad reservada
	 */
	public BigDecimal getReservedQuantity() {
		return reservedQuantity;
	}

	/**
	 * @param reservedQuantity cantidad reservada
	 */
	public void setReservedQuantity(BigDecimal reservedQuantity) {
		this.reservedQuantity = reservedQuantity;
	}

	/**
	 * @return el ultimo cierre disponible
	 */
	public BigDecimal getCurrentPrice() {
		return currentPrice;
	}

	/**
	 * @param currentPrice ultimo cierre disponible
	 */
	public void setCurrentPrice(BigDecimal currentPrice) {
		this.currentPrice = currentPrice;
	}

	/**
	 * @return el valor de mercado de la posicion
	 */
	public BigDecimal getMarketValue() {
		return marketValue;
	}

	/**
	 * @param marketValue valor de mercado de la posicion
	 */
	public void setMarketValue(BigDecimal marketValue) {
		this.marketValue = marketValue;
	}

	/**
	 * @return el costo promedio ponderado de adquisicion
	 */
	public BigDecimal getAverageCost() {
		return averageCost;
	}

	/**
	 * @param averageCost costo promedio ponderado de adquisicion
	 */
	public void setAverageCost(BigDecimal averageCost) {
		this.averageCost = averageCost;
	}

	/**
	 * @return el rendimiento total en porcentaje
	 */
	public BigDecimal getTotalPerformance() {
		return totalPerformance;
	}

	/**
	 * @param totalPerformance rendimiento total en porcentaje
	 */
	public void setTotalPerformance(BigDecimal totalPerformance) {
		this.totalPerformance = totalPerformance;
	}

	/**
	 * @return el rendimiento diario en porcentaje
	 */
	public BigDecimal getDailyPerformance() {
		return dailyPerformance;
	}

	/**
	 * @param dailyPerformance rendimiento diario en porcentaje
	 */
	public void setDailyPerformance(BigDecimal dailyPerformance) {
		this.dailyPerformance = dailyPerformance;
	}

}
