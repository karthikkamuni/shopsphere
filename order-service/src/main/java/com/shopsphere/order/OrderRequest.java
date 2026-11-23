package com.shopsphere.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull Long userId,
        @NotNull Long productId,
        @NotNull @Min(1) @Max(100) Integer quantity) {
}
