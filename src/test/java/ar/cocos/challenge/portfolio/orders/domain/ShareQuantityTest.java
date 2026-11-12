package ar.cocos.challenge.portfolio.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de la cantidad de acciones.
 *
 * <p>Cubren la regla de cantidad entera (spec FR-017) y el reparto de un
 * importe en acciones (spec FR-030, FR-032).</p>
 */
class ShareQuantityTest {

	@Test
	@DisplayName("Acepta una cantidad entera positiva")
	void acceptsWholePositiveQuantity() {
		ShareQuantity quantity = new ShareQuantity(new BigDecimal("10"));

		assertThat(quantity.value()).isEqualByComparingTo("10");
		assertThat(quantity.isZero()).isFalse();
	}

	@Test
	@DisplayName("Rechaza una cantidad con parte fraccionaria")
	void rejectsFractionalQuantity() {
		assertThatThrownBy(() -> new ShareQuantity(new BigDecimal("10.5")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("whole number");
	}

	@Test
	@DisplayName("Rechaza una cantidad negativa")
	void rejectsNegativeQuantity() {
		assertThatThrownBy(() -> new ShareQuantity(new BigDecimal("-1")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not be negative");
	}

	@Test
	@DisplayName("Rechaza una cantidad nula")
	void rejectsNullQuantity() {
		assertThatThrownBy(() -> new ShareQuantity(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Reparte un importe en la cantidad maxima de acciones enteras")
	void splitsAmountIntoWholeShares() {
		// 10.000 / 1.500 = 6 acciones (9.000), sobran 1.000.
		ShareQuantity.AmountSplit split = ShareQuantity.splitByAmount(new BigDecimal("10000"),
				new BigDecimal("1500"));

		assertThat(split.shares()).isEqualByComparingTo("6");
		assertThat(split.remainder()).isEqualByComparingTo("1000");
		assertThat(split.isEmpty()).isFalse();
	}

	@Test
	@DisplayName("Devuelve cero acciones si el importe no alcanza para una")
	void splitsAmountBelowOneShare() {
		ShareQuantity.AmountSplit split = ShareQuantity.splitByAmount(new BigDecimal("1000"),
				new BigDecimal("1500"));

		assertThat(split.shares()).isEqualByComparingTo("0");
		assertThat(split.remainder()).isEqualByComparingTo("1000");
		assertThat(split.isEmpty()).isTrue();
	}

	@Test
	@DisplayName("Reparte exactamente cuando el importe es multiplo del precio")
	void splitsExactMultiple() {
		ShareQuantity.AmountSplit split = ShareQuantity.splitByAmount(new BigDecimal("9000"),
				new BigDecimal("1500"));

		assertThat(split.shares()).isEqualByComparingTo("6");
		assertThat(split.remainder()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Rechaza un precio no positivo al repartir")
	void rejectsNonPositivePrice() {
		assertThatThrownBy(() -> ShareQuantity.splitByAmount(new BigDecimal("100"), BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("greater than zero");
	}

	@Test
	@DisplayName("Compara cantidades por valor numerico")
	void comparesByValue() {
		assertThat(new ShareQuantity(new BigDecimal("10")).compareTo(new ShareQuantity(new BigDecimal("10"))))
				.isZero();
		assertThat(new ShareQuantity(new BigDecimal("9")).compareTo(new ShareQuantity(new BigDecimal("10"))))
				.isNegative();
	}

}
