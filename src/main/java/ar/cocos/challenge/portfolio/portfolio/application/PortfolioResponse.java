package ar.cocos.challenge.portfolio.portfolio.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estado patrimonial de la cuenta en la respuesta de la API (spec FR-067,
 * FR-069, FR-076).
 *
 * <p>Es una clase Java con getters y setters, y no un record, porque
 * ModelMapper 3.2.8 exige un constructor sin argumentos para instanciar el
 * destino del mapeo.</p>
 */
public class PortfolioResponse {

	private BigDecimal totalCash;
	private BigDecimal availableCash;
	private BigDecimal reservedCash;
	private BigDecimal totalValue;
	private List<PositionResponse> positions;

	/**
	 * @return el efectivo total de la cuenta
	 */
	public BigDecimal getTotalCash() {
		return totalCash;
	}

	/**
	 * @param totalCash efectivo total de la cuenta
	 */
	public void setTotalCash(BigDecimal totalCash) {
		this.totalCash = totalCash;
	}

	/**
	 * @return el efectivo disponible
	 */
	public BigDecimal getAvailableCash() {
		return availableCash;
	}

	/**
	 * @param availableCash efectivo disponible
	 */
	public void setAvailableCash(BigDecimal availableCash) {
		this.availableCash = availableCash;
	}

	/**
	 * @return el efectivo reservado
	 */
	public BigDecimal getReservedCash() {
		return reservedCash;
	}

	/**
	 * @param reservedCash efectivo reservado
	 */
	public void setReservedCash(BigDecimal reservedCash) {
		this.reservedCash = reservedCash;
	}

	/**
	 * @return el patrimonio total de la cuenta
	 */
	public BigDecimal getTotalValue() {
		return totalValue;
	}

	/**
	 * @param totalValue patrimonio total de la cuenta
	 */
	public void setTotalValue(BigDecimal totalValue) {
		this.totalValue = totalValue;
	}

	/**
	 * @return las posiciones en instrumentos
	 */
	public List<PositionResponse> getPositions() {
		return positions;
	}

	/**
	 * @param positions posiciones en instrumentos
	 */
	public void setPositions(List<PositionResponse> positions) {
		this.positions = positions;
	}

}
