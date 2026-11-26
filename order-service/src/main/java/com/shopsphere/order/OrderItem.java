package com.shopsphere.order;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="order_items",indexes=@Index(name="idx_order_items_order",columnList="order_id"))
public class OrderItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private Order order;
 @Column(nullable=false) private Long productId; @Column(nullable=false,length=150) private String productName;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice; @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
 @Column(nullable=false,length=36) private String reservationId;
 @Column(nullable=false,length=20,columnDefinition="varchar(20) default 'PENDING'") private String reservationStatus="PENDING";
 protected OrderItem(){}
 public OrderItem(Long productId,String name,BigDecimal price,int quantity,String reservationId){this.productId=productId;this.productName=name;this.unitPrice=price;this.quantity=quantity;this.subtotal=price.multiply(BigDecimal.valueOf(quantity));this.reservationId=reservationId;}
 void setOrder(Order order){this.order=order;} public Long getId(){return id;} public Long getProductId(){return productId;} public String getProductName(){return productName;}
 public BigDecimal getUnitPrice(){return unitPrice;} public int getQuantity(){return quantity;} public BigDecimal getSubtotal(){return subtotal;} public String getReservationId(){return reservationId;}
 public String getReservationStatus(){return reservationStatus;} public Order getOrder(){return order;}
}
record ReservationSettlement(Long orderId,OrderStatus orderStatus,String reservationId,String reservationStatus){}
