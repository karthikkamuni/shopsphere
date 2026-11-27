package com.shopsphere.order;
import org.springframework.data.domain.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;import java.util.*;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
@Service public class OrderService {
 private static final Logger log=LoggerFactory.getLogger(OrderService.class);
 private final CartItemRepository cart;private final CartService carts;private final OrderPersistenceService persistence;private final OrderRepository orders;
 private final ProductClient products;private final UserClient users;
 public OrderService(CartItemRepository cart,CartService carts,OrderPersistenceService persistence,OrderRepository orders,ProductClient products,UserClient users){this.cart=cart;this.carts=carts;this.persistence=persistence;this.orders=orders;this.products=products;this.users=users;}
 public void requireActive(Long user){if(user==null||!users.active(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account does not exist or is blocked");}
 public CartResponse viewCart(Long user){requireActive(user);List<CartLineResponse> rows=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;int count=0;
  for(CartItem item:carts.items(user)){ProductDto p=products.findProduct(item.getProductId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"A cart product is no longer available"));BigDecimal sub=p.price().multiply(BigDecimal.valueOf(item.getQuantity()));total=total.add(sub);count+=item.getQuantity();rows.add(new CartLineResponse(p.id(),p.name(),p.description(),p.category(),p.imageUrl(),p.price(),item.getQuantity(),sub,p.stock(),p.available()));}
  return new CartResponse(rows,total,count);}
 public OrderResponse checkoutCart(Long user,String checkoutKey){return checkout(user,checkoutKey,null);}
 public OrderResponse checkoutSingle(Long user,String checkoutKey,OrderRequest request){if(!Objects.equals(user,request.userId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Order user must match authenticated account");return checkout(user,checkoutKey,List.of(new CartItem(user,request.productId(),request.quantity())));}
 private OrderResponse checkout(Long user,String key,List<CartItem> lines){requireActive(user);String normalized=validKey(key);
  Optional<OrderResponse> old=persistence.byKey(normalized);if(old.isPresent()){settle(old.get());return old.get();}
  List<CartItem> selected=lines==null?carts.items(user):lines;if(selected.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Shopping cart is empty");
  List<Reserved> reserved=new ArrayList<>();List<OrderItem> snapshots=new ArrayList<>();
  try{
   for(CartItem line:selected){ProductDto p=products.findProduct(line.getProductId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Product is no longer available"));if(!p.available()||p.price()==null||p.name()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is unavailable");
    String reservation=UUID.randomUUID().toString();ReservationResponse r=products.reserve(p.id(),line.getQuantity(),reservation);reserved.add(new Reserved(reservation));snapshots.add(new OrderItem(p.id(),p.name(),p.price(),line.getQuantity(),reservation));}
   log.info("Persisting checkout {} for user {} with {} item(s)",normalized,user,snapshots.size());
   OrderResponse saved=OrderResponse.from(persistence.save(user,normalized,snapshots,lines==null));
   settle(saved);return saved;
  }catch(RuntimeException e){Set<String> stored=new HashSet<>(persistence.reservationIdsByKey(normalized));release(reserved.stream().filter(r->!stored.contains(r.id())).toList());
   if(!stored.isEmpty()){OrderResponse existing=persistence.byKey(normalized).orElseThrow(()->e);settle(existing);return existing;}throw e;}
 }
 private void settle(OrderResponse response){for(ReservationSettlement reservation:persistence.reservations(response.id()))if("PENDING".equals(reservation.reservationStatus())){try{products.commit(reservation.reservationId());persistence.markReservation(reservation.reservationId(),"PENDING","COMMITTED");}catch(RuntimeException e){log.warn("Could not commit reservation {} for order {}; reconciliation will retry",reservation.reservationId(),response.id(),e);}}}
 private void release(List<Reserved> list){for(Reserved r:list)try{products.release(r.id());}catch(RuntimeException e){log.error("Could not release product reservation {}; reconciliation is required",r.id(),e);}}
 private String validKey(String key){try{return UUID.fromString(key).toString();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must be a UUID");}}
 public OrderResponse get(Long id,Long user,boolean admin){requireActive(user);OrderResponse o=persistence.byId(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));if(!admin&&!o.userId().equals(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Order belongs to another account");return admin?withCustomer(o):o;}
 public Page<OrderResponse> list(Long user,boolean admin,OrderStatus status,Long filterUser,int page,int size){requireActive(user);if(size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"size must be 1..100");Page<OrderResponse> result=persistence.search(admin?null:user,admin?filterUser:null,status,PageRequest.of(Math.max(0,page),size,Sort.by(Sort.Direction.DESC,"createdAt")));return admin?result.map(this::withCustomer):result;}
 public OrderResponse transition(Long id,OrderStatus next){OrderStatus current=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found")).getStatus();boolean retryCancellation=current==OrderStatus.CANCELLED&&next==OrderStatus.CANCELLED;if(!retryCancellation&&!validTransition(current,next))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid order status transition");
  OrderResponse result=retryCancellation?persistence.byId(id).orElseThrow():persistence.updateStatus(id,current,next);
  if(next==OrderStatus.CANCELLED)releaseOrderReservations(id);
  return result;}
 private boolean validTransition(OrderStatus from,OrderStatus to){return switch(from){case CREATED,PLACED->to==OrderStatus.CONFIRMED||to==OrderStatus.CANCELLED;case CONFIRMED->to==OrderStatus.PROCESSING||to==OrderStatus.CANCELLED;case PROCESSING->to==OrderStatus.SHIPPED;case SHIPPED->to==OrderStatus.DELIVERED;case DELIVERED,CANCELLED->false;};}
 public DashboardResponse dashboard(Long adminId){requireActive(adminId);List<OrderResponse> recent=persistence.search(null,null,null,PageRequest.of(0,10,Sort.by(Sort.Direction.DESC,"createdAt"))).getContent().stream().map(this::withCustomer).toList();return new DashboardResponse(orders.count(),orders.countPending(OrderStatus.DELIVERED,OrderStatus.CANCELLED),orders.countByStatus(OrderStatus.DELIVERED),orders.countByStatus(OrderStatus.CANCELLED),orders.revenueFor(OrderStatus.DELIVERED),recent);}
 private OrderResponse withCustomer(OrderResponse order){return order.withCustomer(users.details(order.userId()));}
 private void releaseOrderReservations(Long orderId){for(ReservationSettlement reservation:persistence.reservations(orderId)){try{products.release(reservation.reservationId());persistence.markReservation(reservation.reservationId(),reservation.reservationStatus(),"RELEASED");}catch(RuntimeException e){log.error("Could not release reservation {} for order {}; reconciliation will retry",reservation.reservationId(),orderId,e);}}}
 @Scheduled(initialDelayString="${shopsphere.reservations.reconcile-initial-delay-ms:15000}",fixedDelayString="${shopsphere.reservations.reconcile-interval-ms:30000}")
 public void reconcileReservationSettlements(){
  for(ReservationSettlement reservation:persistence.unsettledReservations()){
   try{
    if(reservation.orderStatus()==OrderStatus.CANCELLED){products.release(reservation.reservationId());persistence.markReservation(reservation.reservationId(),reservation.reservationStatus(),"RELEASED");}
    else if("PENDING".equals(reservation.reservationStatus())){products.commit(reservation.reservationId());persistence.markReservation(reservation.reservationId(),"PENDING","COMMITTED");}
   }catch(RuntimeException e){log.warn("Reservation settlement retry deferred for order {} reservation {}",reservation.orderId(),reservation.reservationId(),e);}
  }
 }
 private record Reserved(String id){}
}
