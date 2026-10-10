package br.com.fiap.delivery.order.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotNull(message = "is required") Long dishId,
        @NotNull(message = "is required")
        @Min(value = 1, message = "must be between 1 and 5")
        @Max(value = 5, message = "must be between 1 and 5") Integer rating,
        String comment) {
}
