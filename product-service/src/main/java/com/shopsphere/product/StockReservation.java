package com.shopsphere.product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="stock_reservations",indexes=@Index(name="idx_reservation_product",columnList="productId"))
public class StockReservation {
 @Id private String id; @Column(nullable=false) private Long productId; @Column(nullable=false) private int quantity;
 @Column(nullable=false,length=20) private String status; @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 protected StockReservation(){} public StockReservation(String id,Long productId,int quantity){this.id=id;this.productId=productId;this.quantity=quantity;this.status="RESERVED";}
 public String getId(){return id;} public Long getProductId(){return productId;} public int getQuantity(){return quantity;} public String getStatus(){return status;} public void setStatus(String s){status=s;}
}
