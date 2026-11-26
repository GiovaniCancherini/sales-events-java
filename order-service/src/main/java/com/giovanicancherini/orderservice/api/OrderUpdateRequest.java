package com.giovanicancherini.orderservice.api;

import com.giovanicancherini.orderservice.Cents;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderUpdateRequest(
        @NotNull @Positive @Max(Cents.MAXIMUM) Long totalAmountInCents
) {
}
