package com.shopsphere.order;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.util.List;import java.util.Optional;
@Service public class OrderPersistenceService {
 private final OrderRepository orders;private final CartItemRepository cart;private final OrderItemRepository items;
 public OrderPersistenceService(OrderRepository orders,CartItemRepository cart,OrderItemRepository items){this.orders=orders;this.cart=cart;this.items=items;}
 @Transactional public Order save(Long userId,String key,List<OrderItem> items,boolean clearCart){Order order=new Order(userId,key);items.forEach(order::addItem);Order saved=orders.save(order);if(clearCart)cart.deleteByUserId(userId);return saved;}
 @Transactional(readOnly=true) public Optional<OrderResponse> byKey(String key){return orders.findByCheckoutKey(key).map(OrderResponse::from);}
 @Transactional(readOnly=true) public Optional<OrderResponse> byId(Long id){return orders.findById(id).map(OrderResponse::from);}
 @Transactional(readOnly=true) public List<String> reservationIds(Long id){return orders.findById(id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Order not found")).getItems().stream().map(OrderItem::getReservationId).toList();}
 @Transactional(readOnly=true) public List<ReservationSettlement> reservations(Long id){return items.findForOrder(id,List.of("PENDING","COMMITTED")).stream().map(i->new ReservationSettlement(i.getOrder().getId(),i.getOrder().getStatus(),i.getReservationId(),i.getReservationStatus())).toList();}
 @Transactional(readOnly=true) public List<ReservationSettlement> unsettledReservations(){return items.findForReconciliation(List.of("PENDING","COMMITTED")).stream().map(i->new ReservationSettlement(i.getOrder().getId(),i.getOrder().getStatus(),i.getReservationId(),i.getReservationStatus())).toList();}
 @Transactional public void markReservation(String id,String current,String next){items.transitionReservation(id,current,next);}
 @Transactional(readOnly=true) public List<String> reservationIdsByKey(String key){return orders.findByCheckoutKey(key).map(o->o.getItems().stream().map(OrderItem::getReservationId).toList()).orElseGet(List::of);}
 @Transactional(readOnly=true) public org.springframework.data.domain.Page<OrderResponse> search(Long user,Long filterUser,OrderStatus status,org.springframework.data.domain.Pageable page){return orders.search(user,filterUser,status,page).map(OrderResponse::from);}
 @Transactional public OrderResponse updateStatus(Long id,OrderStatus current,OrderStatus next){if(orders.transition(id,current,next,java.time.Instant.now())!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Order status changed concurrently; reload and retry");return orders.findById(id).map(OrderResponse::from).orElseThrow();}
}
