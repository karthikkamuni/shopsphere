package com.shopsphere.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String status,
        Instant createdAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getProductId(),
                order.getQuantity(), order.getUnitPrice(), order.getTotalPrice(),
                order.getStatus(), order.getCreatedAt());
    }
}
