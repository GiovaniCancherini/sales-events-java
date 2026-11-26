package com.giovanicancherini.orderservice.api;

import com.giovanicancherini.orderservice.Cents;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record OrderCreateRequest(
        @NotBlank String customerId,
        @NotBlank String product,
        @NotNull @Positive @Max(Cents.MAXIMUM) Long totalAmountInCents
) {
}
