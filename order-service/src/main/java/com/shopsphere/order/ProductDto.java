package com.shopsphere.order;

import java.math.BigDecimal;

/** The part of product-service's response that order-service needs. Unknown JSON fields are ignored. */
public record ProductDto(Long id, String name, BigDecimal price) {
}
