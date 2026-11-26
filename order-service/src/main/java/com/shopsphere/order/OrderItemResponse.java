package com.shopsphere.order;
import java.math.BigDecimal;
public record OrderItemResponse(Long id,Long productId,String productName,BigDecimal unitPrice,int quantity,BigDecimal subtotal){
 static OrderItemResponse from(OrderItem i){return new OrderItemResponse(i.getId(),i.getProductId(),i.getProductName(),i.getUnitPrice(),i.getQuantity(),i.getSubtotal());}
}
