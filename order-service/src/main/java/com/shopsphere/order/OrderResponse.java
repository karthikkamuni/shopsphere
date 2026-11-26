package com.shopsphere.order;
import java.math.BigDecimal;import java.time.Instant;import java.util.List;
public record OrderResponse(Long id,Long userId,Long productId,int quantity,BigDecimal unitPrice,BigDecimal totalPrice,String status,Instant createdAt,List<OrderItemResponse> items,CustomerDetails customer){
 static OrderResponse from(Order o){List<OrderItemResponse> items=o.getItems().stream().map(OrderItemResponse::from).toList();
  return new OrderResponse(o.getId(),o.getUserId(),o.getProductId(),o.getQuantity(),o.getUnitPrice(),o.getTotalPrice(),o.getStatus().name(),o.getCreatedAt(),items,null);}
 OrderResponse withCustomer(CustomerDetails details){return new OrderResponse(id,userId,productId,quantity,unitPrice,totalPrice,status,createdAt,items,details);}
}
record CartLineResponse(Long productId,String productName,String description,String category,String imageUrl,java.math.BigDecimal unitPrice,int quantity,java.math.BigDecimal subtotal,int availableStock,boolean available){}
record CartResponse(List<CartLineResponse> items,java.math.BigDecimal total,int itemCount){}
record OrderPageResponse(List<OrderResponse> content,int number,int size,long totalElements,int totalPages){}
record DashboardResponse(long totalOrders,long pendingOrders,long deliveredOrders,long cancelledOrders,java.math.BigDecimal revenue,List<OrderResponse> recentOrders){}
