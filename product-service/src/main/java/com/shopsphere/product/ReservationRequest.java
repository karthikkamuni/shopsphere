package com.shopsphere.product;
import jakarta.validation.constraints.Min;
record ReservationRequest(@Min(1) int quantity) {}
record ReservationResponse(String reservationId,String status) {}
