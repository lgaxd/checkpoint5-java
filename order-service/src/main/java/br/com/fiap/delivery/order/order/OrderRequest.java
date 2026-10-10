package br.com.fiap.delivery.order.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull(message = "is required") Long dishId,
        @NotNull(message = "is required") @Min(value = 1, message = "must be at least 1") Integer quantity) {
}
