package ar.cocos.challenge.portfolio.orders.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import ar.cocos.challenge.portfolio.instruments.domain.Instrument;
import ar.cocos.challenge.portfolio.instruments.domain.InstrumentType;
import ar.cocos.challenge.portfolio.shared.domain.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de las reglas de dominio de las transferencias de efectivo.
 *
 * <p>Cubren el conjunto de reglas propio de las transferencias (spec FR-020,
 * FR-022, FR-025, D-018).</p>
 */
class TransferPolicyTest {

	private final Instrument cash = new Instrument(11L, "ARS", "Peso argentino", InstrumentType.MONEDA);

	private final Instrument stock = new Instrument(10L, "YPFD", "Y.P.F. S.A.", InstrumentType.ACCIONES);

	@Test
	@DisplayName("Acepta una transferencia valida sobre el instrumento de efectivo")
	void acceptsValidTransfer() {
		assertThatCode(() -> TransferPolicy.validate(cash, OrderType.MARKET, null, new BigDecimal("1000.00"), null))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Rechaza una transferencia sobre un instrumento que no es el de efectivo")
	void rejectsTransferOnNonCashInstrument() {
		assertThatThrownBy(() -> TransferPolicy.validate(stock, OrderType.MARKET, null, new BigDecimal("1000.00"), null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("ARS");
	}

	@Test
	@DisplayName("Rechaza una transferencia que no es de tipo MARKET")
	void rejectsNonMarketTransfer() {
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.LIMIT, null, new BigDecimal("1000.00"), null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("MARKET");
	}

	@Test
	@DisplayName("Rechaza una transferencia con precio")
	void rejectsTransferWithPrice() {
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.MARKET, new BigDecimal("1.00"),
				new BigDecimal("1000.00"), null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("must not provide a price");
	}

	@Test
	@DisplayName("Rechaza una transferencia expresada con importe")
	void rejectsTransferWithAmount() {
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.MARKET, null, null, new BigDecimal("1000.00")))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("'size'");
	}

	@Test
	@DisplayName("Rechaza una transferencia sin importe")
	void rejectsTransferWithoutAmount() {
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.MARKET, null, null, null))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("positive amount");
	}

	@Test
	@DisplayName("Rechaza una transferencia con importe cero o negativo")
	void rejectsTransferWithNonPositiveAmount() {
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.MARKET, null, BigDecimal.ZERO, null))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> TransferPolicy.validate(cash, OrderType.MARKET, null, new BigDecimal("-100.00"), null))
				.isInstanceOf(InvalidRequestException.class);
	}

}
