package com.giovanicancherini.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

	@Test
	void totalAmountAlwaysHasTwoDecimals() {
		Order order = new Order("cliente-1", "Notebook", new BigDecimal("4500.5"));

		assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("4500.50"));
	}

	@Test
	void rejectsMoreThanTwoDecimalsInsteadOfRounding() {
		assertThatThrownBy(() -> new Order("cliente-1", "Notebook", new BigDecimal("10.555")))
				.isInstanceOf(ArithmeticException.class);
	}

}
