package com.giovanicancherini.orderservice.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderCreateRequest(
        @NotBlank String customerId,
        @NotBlank String product,
        @NotNull @DecimalMin(value = "0.01") BigDecimal totalAmount
) {
}
