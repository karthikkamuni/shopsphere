package com.shopsphere.order;

import java.math.BigDecimal;

/** The part of product-service's response that order-service needs. Unknown JSON fields are ignored. */
public record ProductDto(Long id, String name, String description, BigDecimal price, String category, String imageUrl, int stock, boolean available) {
}
