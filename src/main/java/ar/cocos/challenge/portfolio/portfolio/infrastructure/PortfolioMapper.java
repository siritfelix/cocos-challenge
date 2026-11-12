package ar.cocos.challenge.portfolio.portfolio.infrastructure;

import java.util.List;

import ar.cocos.challenge.portfolio.portfolio.application.PortfolioResponse;
import ar.cocos.challenge.portfolio.portfolio.application.PositionResponse;
import ar.cocos.challenge.portfolio.portfolio.domain.Portfolio;
import ar.cocos.challenge.portfolio.portfolio.domain.Position;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/**
 * Mapeador entre el portfolio del dominio y los DTOs de la API.
 *
 * <p>El mapeo se declara con un {@link org.modelmapper.Converter} explicito
 * porque ModelMapper 3.2.8 no puede instanciar records ni mapear tipos
 * finales, y tanto {@link Portfolio} como {@link Position} son records
 * (research documentado en {@code ModelMapperConfig}).</p>
 */
@Component
public class PortfolioMapper {

	private final ModelMapper modelMapper;

	/**
	 * @param modelMapper mapeador configurado por la aplicacion
	 */
	public PortfolioMapper(ModelMapper modelMapper) {
		this.modelMapper = modelMapper;
		this.modelMapper.createTypeMap(Position.class, PositionResponse.class)
				.setConverter(context -> buildPosition(context.getSource()));
		this.modelMapper.createTypeMap(Portfolio.class, PortfolioResponse.class)
				.setConverter(context -> buildPortfolio(context.getSource()));
	}

	/**
	 * Convierte el portfolio del dominio en su DTO de respuesta.
	 *
	 * @param portfolio portfolio del dominio
	 * @return el DTO de respuesta
	 */
	public PortfolioResponse toResponse(Portfolio portfolio) {
		return modelMapper.map(portfolio, PortfolioResponse.class);
	}

	private PortfolioResponse buildPortfolio(Portfolio portfolio) {
		PortfolioResponse response = new PortfolioResponse();
		response.setTotalCash(portfolio.totalCash());
		response.setAvailableCash(portfolio.availableCash());
		response.setReservedCash(portfolio.reservedCash());
		response.setTotalValue(portfolio.totalValue());
		List<PositionResponse> positions = portfolio.positions().stream()
				.map(this::buildPosition)
				.toList();
		response.setPositions(positions);
		return response;
	}

	private PositionResponse buildPosition(Position position) {
		PositionResponse response = new PositionResponse();
		response.setTicker(position.ticker());
		response.setTotalQuantity(position.totalQuantity());
		response.setAvailableQuantity(position.availableQuantity());
		response.setReservedQuantity(position.reservedQuantity());
		response.setCurrentPrice(position.currentPrice());
		response.setMarketValue(position.marketValue());
		response.setAverageCost(position.averageCost());
		response.setTotalPerformance(position.totalPerformance());
		response.setDailyPerformance(position.dailyPerformance());
		return response;
	}

}
