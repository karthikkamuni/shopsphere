package com.shopsphere.product;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ProductRequest(@NotBlank @Size(max=150) String name,@Size(max=1000) String description,
 @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,
 @Size(max=80) String category,@Size(max=1000) String imageUrl,
 @Min(0) int stock, Boolean available) {}
record StockRequest(@Min(0) int quantity) {}
record AvailabilityRequest(boolean available) {}
