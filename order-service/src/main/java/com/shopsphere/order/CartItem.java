package com.shopsphere.order;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="cart_items",uniqueConstraints=@UniqueConstraint(name="uk_cart_user_product",columnNames={"userId","productId"}),indexes=@Index(name="idx_cart_user",columnList="userId"))
public class CartItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long userId; @Column(nullable=false) private Long productId; @Column(nullable=false) private int quantity;
 @Column(nullable=false) private Instant updatedAt=Instant.now();
 protected CartItem(){} public CartItem(Long user,Long product,int quantity){userId=user;productId=product;this.quantity=quantity;}
 public void setQuantity(int q){quantity=q;updatedAt=Instant.now();} public Long getId(){return id;} public Long getUserId(){return userId;} public Long getProductId(){return productId;} public int getQuantity(){return quantity;}
}
