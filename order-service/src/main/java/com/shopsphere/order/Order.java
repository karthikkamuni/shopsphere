package com.shopsphere.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single-product order. The table is called "orders" because "order" is a
 * reserved SQL keyword. userId/productId are plain numbers (not foreign keys):
 * each microservice owns its own database and never joins across them.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    // Price at the time of ordering (a snapshot, not a live reference).
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, length = 20)
    private String status = "CREATED";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Order() {
        // required by JPA
    }

    public Order(Long userId, Long productId, int quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
