package com.shopsphere.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Releases abandoned reservations after the order-service recovery window has elapsed. */
@Component
public class StockReservationReconciler {
    private static final Logger log = LoggerFactory.getLogger(StockReservationReconciler.class);

    private final ProductService products;
    private final long leaseMillis;

    public StockReservationReconciler(ProductService products,
            @Value("${shopsphere.reservations.lease-ms:3600000}") long leaseMillis) {
        this.products = products;
        this.leaseMillis = Math.max(leaseMillis, 60_000);
    }

    @Scheduled(fixedDelayString = "${shopsphere.reservations.reconcile-interval-ms:60000}")
    public void reconcile() {
        int released = products.reconcileExpiredReservations(Instant.now().minusMillis(leaseMillis));
        if (released > 0) {
            log.info("Released {} abandoned stock reservations", released);
        }
    }
}
