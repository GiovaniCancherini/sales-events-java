package com.giovanicancherini.orderservice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CentsTest {

	@Test
	void convertsBothWaysWithoutLosingCents() {
		assertThat(Cents.fromAmount(new BigDecimal("4500.50"))).isEqualTo(450050L);
		assertThat(Cents.toAmount(450050L)).isEqualTo(new BigDecimal("4500.50"));
		assertThat(Cents.toAmount(Cents.MAXIMUM)).isEqualTo(new BigDecimal("9999999999999.99"));
	}

	@Test
	void amountWithMoreThanTwoDecimalsIsABug() {
		assertThatThrownBy(() -> Cents.fromAmount(new BigDecimal("10.555")))
				.isInstanceOf(ArithmeticException.class);
	}
}
