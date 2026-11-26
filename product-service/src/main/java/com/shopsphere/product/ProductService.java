package com.shopsphere.product;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service public class ProductService {
 private final ProductRepository products; private final StockReservationRepository reservations;
 public ProductService(ProductRepository products,StockReservationRepository reservations){this.products=products;this.reservations=reservations;}
 public List<ProductResponse> browse(String q,String category,String sort){
  List<Product> rows=(category!=null&&!category.isBlank())?(q!=null&&!q.isBlank()?products.findByActiveTrueAndCategoryIgnoreCaseAndNameContainingIgnoreCase(category,q):products.findByActiveTrueAndCategoryIgnoreCase(category)):(q!=null&&!q.isBlank()?products.findByActiveTrueAndNameContainingIgnoreCase(q):products.findByActiveTrue());
  Comparator<Product> cmp=Comparator.comparing(Product::getName,String.CASE_INSENSITIVE_ORDER);
  if("price,asc".equals(sort))cmp=Comparator.comparing(Product::getPrice); else if("price,desc".equals(sort))cmp=Comparator.comparing(Product::getPrice).reversed();
  rows.sort(cmp); return rows.stream().map(ProductResponse::from).toList();
 }
 public Page<ProductResponse> page(int page,int size,String sort){if(size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"size must be 1..100");
  Sort s="price,asc".equals(sort)?Sort.by("price"):"price,desc".equals(sort)?Sort.by("price").descending():Sort.by("name");
  return products.findByActiveTrue(PageRequest.of(Math.max(0,page),size,s)).map(ProductResponse::from);}
 public ProductResponse get(Long id){return ProductResponse.from(active(id));}
 public List<ProductResponse> inactive(){return products.findByActiveFalseOrderByNameAsc().stream().map(ProductResponse::from).toList();}
 @Transactional public ProductResponse create(ProductRequest r){return ProductResponse.from(products.save(new Product(r)));}
 @Transactional public ProductResponse update(Long id,ProductRequest r){Product p=active(id);p.update(r);return ProductResponse.from(p);}
 @Transactional public ProductResponse deactivate(Long id){Product p=active(id);p.setActive(false);return ProductResponse.from(p);}
 @Transactional public ProductResponse reactivate(Long id){Product p=products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product "+id+" not found"));p.setActive(true);return ProductResponse.from(p);}
 @Transactional public ProductResponse stock(Long id,int stock){Product p=active(id);p.setStock(stock);return ProductResponse.from(p);}
 @Transactional public ProductResponse availability(Long id,boolean available){Product p=active(id);p.setAvailable(available);return ProductResponse.from(p);}
 @Transactional public ReservationResponse reserve(Long id,int qty,String key){
  try { java.util.UUID.fromString(key); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must be a UUID"); }
  Optional<StockReservation> previous=reservations.findById(key); if(previous.isPresent()){StockReservation r=previous.get();if(!r.getProductId().equals(id)||r.getQuantity()!=qty)throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key reused with different reservation");if("RELEASED".equals(r.getStatus())||"RESTOCKED".equals(r.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation was already released");return new ReservationResponse(key,r.getStatus());}
  if(products.reserveStock(id,qty)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Product unavailable or insufficient stock");
  reservations.save(new StockReservation(key,id,qty));return new ReservationResponse(key,"RESERVED");
 }
 @Transactional public ReservationResponse settle(String key,boolean commit){StockReservation r=reservations.findById(key).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Reservation not found"));
  if(commit){reservations.markCommitted(key);}
  else {int changed=reservations.markReleased(key);if(changed==0)changed=reservations.markRestocked(key);if(changed==1)products.returnStock(r.getProductId(),r.getQuantity());}
  return new ReservationResponse(key,reservations.findById(key).orElseThrow().getStatus());
 }
 @Transactional public int reconcileExpiredReservations(java.time.Instant cutoff){
  var expired=reservations.findByStatusAndCreatedAtBefore("RESERVED",cutoff,PageRequest.of(0,100)).getContent();
  int released=0;
  for(StockReservation reservation:expired){
   if(reservations.releaseExpired(reservation.getId(),cutoff)==1){
    products.returnStock(reservation.getProductId(),reservation.getQuantity());
    released++;
   }
  }
  return released;
 }
 private Product active(Long id){return products.findById(id).filter(Product::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product "+id+" not found"));}
}
