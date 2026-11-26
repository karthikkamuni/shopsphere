package com.shopsphere.order;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name="orders",indexes={@Index(name="idx_orders_user_created",columnList="userId,createdAt"),@Index(name="idx_orders_status",columnList="status")})
public class Order {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long userId;
 @Column(length=36,unique=true) private String checkoutKey;
 // Retained for compatibility with the original single-item orders table; items are now canonical snapshots.
 @Column(nullable=false) private Long productId;
 @Column(nullable=false) private int quantity;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OrderStatus status=OrderStatus.PLACED;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal totalPrice=BigDecimal.ZERO;
 @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 @Column(nullable=false) private Instant updatedAt=Instant.now();
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.LAZY) private List<OrderItem> items=new ArrayList<>();
 protected Order(){}
 public Order(Long userId,String key){this.userId=userId;this.checkoutKey=key;}
 public void addItem(OrderItem item){if(items.isEmpty()){productId=item.getProductId();quantity=item.getQuantity();unitPrice=item.getUnitPrice();}item.setOrder(this);items.add(item);totalPrice=totalPrice.add(item.getSubtotal());}
 public void setStatus(OrderStatus status){this.status=status;this.updatedAt=Instant.now();}
 public Long getId(){return id;} public Long getUserId(){return userId;} public String getCheckoutKey(){return checkoutKey;}
 public OrderStatus getStatus(){return status;} public BigDecimal getTotalPrice(){return totalPrice;} public Instant getCreatedAt(){return createdAt;}
 public Long getProductId(){return productId;} public int getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;}
 public Instant getUpdatedAt(){return updatedAt;} public List<OrderItem> getItems(){return List.copyOf(items);}
}
