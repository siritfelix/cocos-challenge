package ar.cocos.challenge.portfolio.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios del value object monetario.
 *
 * <p>Verifican la precision decimal exacta y el redondeo explicito exigidos por
 * la spec (FR-096, D-050).</p>
 */
class MoneyTest {

	@Test
	@DisplayName("Normaliza a dos decimales al construir el importe")
	void normalizesScale() {
		Money money = Money.of(new BigDecimal("1500.5"));

		assertThat(money.amount()).isEqualTo(new BigDecimal("1500.50"));
		assertThat(money.amount().scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("Aplica redondeo HALF_UP en la construccion")
	void appliesHalfUpRounding() {
		Money money = Money.of(new BigDecimal("10.005"));

		assertThat(money.amount()).isEqualTo(new BigDecimal("10.01"));
	}

	@Test
	@DisplayName("Suma y resta conservando la escala de dos decimales")
	void addsAndSubtracts() {
		Money base = Money.of("100.00");

		Money sum = base.plus(Money.of("50.25"));
		Money difference = base.minus(Money.of("50.25"));

		assertThat(sum.amount()).isEqualTo(new BigDecimal("150.25"));
		assertThat(difference.amount()).isEqualTo(new BigDecimal("49.75"));
	}

	@Test
	@DisplayName("Multiplica por un factor sin perder precision decimal")
	void multipliesWithoutFloatingPointDrift() {
		Money money = Money.of("10.10").multipliedBy(new BigDecimal("3"));

		// Con double, 10.10 * 3 no seria exactamente 30.30.
		assertThat(money.amount()).isEqualTo(new BigDecimal("30.30"));
	}

	@Test
	@DisplayName("Compara por valor ignorando la escala")
	void comparesByValueIgnoringScale() {
		assertThat(Money.of("10.00")).isEqualTo(Money.of("10"));
		assertThat(Money.of("10.00").compareTo(Money.of("10"))).isZero();
		assertThat(Money.of("9.99").compareTo(Money.of("10.00"))).isNegative();
	}

	@Test
	@DisplayName("Distingue los signos")
	void distinguishesSigns() {
		assertThat(Money.of("1.00").isPositive()).isTrue();
		assertThat(Money.zero().isZero()).isTrue();
		assertThat(Money.of("-1.00").isNegative()).isTrue();
	}

	@Test
	@DisplayName("Rechaza un importe nulo")
	void rejectsNullAmount() {
		assertThatThrownBy(() -> Money.of((BigDecimal) null)).isInstanceOf(NullPointerException.class);
	}

}
