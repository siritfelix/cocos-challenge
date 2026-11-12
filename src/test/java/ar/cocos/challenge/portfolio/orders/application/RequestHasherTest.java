package ar.cocos.challenge.portfolio.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del calculo de la huella del request.
 *
 * <p>Cubren la independencia del orden de las propiedades y la normalizacion de
 * los valores (spec FR-056).</p>
 */
class RequestHasherTest {

	private final RequestHasher requestHasher = new RequestHasher();

	@Test
	@DisplayName("Produce una huella SHA-256 de 64 caracteres hexadecimales")
	void producesSha256HexDigest() {
		String hash = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));

		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("La misma solicitud produce la misma huella")
	void isDeterministic() {
		String first = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));
		String second = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));

		assertThat(first).isEqualTo(second);
	}

	@Test
	@DisplayName("Normaliza la escala numerica: 10 y 10.00 producen la misma huella")
	void normalizesNumericScale() {
		String integerForm = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));
		String decimalForm = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10.00", null, null));

		assertThat(integerForm).isEqualTo(decimalForm);
	}

	@Test
	@DisplayName("Normaliza el ticker: mayusculas y espacios irrelevan")
	void normalizesTicker() {
		String upper = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));
		String padded = requestHasher.hash(request("  ypfd ", OrderSide.BUY, OrderType.MARKET, "10", null, null));

		assertThat(upper).isEqualTo(padded);
	}

	@Test
	@DisplayName("Distingue una cantidad de un importe")
	void distinguishesSizeFromAmount() {
		String withSize = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "100", null, null));
		String withAmount = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, null, "100", null));

		assertThat(withSize).isNotEqualTo(withAmount);
	}

	@Test
	@DisplayName("Cambia la huella al cambiar cualquier campo semantico")
	void changesWithSemanticContent() {
		String baseline = requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "10", null, null));

		assertThat(requestHasher.hash(request("MELI", OrderSide.BUY, OrderType.MARKET, "10", null, null)))
				.isNotEqualTo(baseline);
		assertThat(requestHasher.hash(request("YPFD", OrderSide.SELL, OrderType.MARKET, "10", null, null)))
				.isNotEqualTo(baseline);
		assertThat(requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.LIMIT, "10", null, null)))
				.isNotEqualTo(baseline);
		assertThat(requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.MARKET, "11", null, null)))
				.isNotEqualTo(baseline);
		assertThat(requestHasher.hash(request("YPFD", OrderSide.BUY, OrderType.LIMIT, "10", null, "25000")))
				.isNotEqualTo(baseline);
	}

	private CreateOrderRequest request(String ticker, OrderSide side, OrderType type, String size, String amount,
			String price) {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setTicker(ticker);
		request.setSide(side);
		request.setType(type);
		if (size != null) {
			request.setSize(new BigDecimal(size));
		}
		if (amount != null) {
			request.setAmount(new BigDecimal(amount));
		}
		if (price != null) {
			request.setPrice(new BigDecimal(price));
		}
		return request;
	}

}
