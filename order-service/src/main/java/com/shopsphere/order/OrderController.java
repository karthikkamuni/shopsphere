package com.shopsphere.order;
import jakarta.validation.Valid;import org.springframework.data.domain.Page;import org.springframework.http.HttpStatus;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.core.Authentication;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api")
public class OrderController {
 private final OrderService service;private final CartService carts;
 public OrderController(OrderService service,CartService carts){this.service=service;this.carts=carts;}
 @PostMapping("/cart/items") @ResponseStatus(HttpStatus.CREATED) public CartResponse add(@Valid @RequestBody CartAddRequest r,Authentication a){Long uid=userId(a);service.requireActive(uid);carts.add(uid,r.productId(),r.quantity());return service.viewCart(uid);}
 @GetMapping("/cart") public CartResponse cart(Authentication a){return service.viewCart(userId(a));}
 @PutMapping("/cart/items/{productId}") public CartResponse quantity(@PathVariable Long productId,@Valid @RequestBody CartQuantityRequest r,Authentication a){Long uid=userId(a);service.requireActive(uid);carts.set(uid,productId,r.quantity());return service.viewCart(uid);}
 @DeleteMapping("/cart/items/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable Long productId,Authentication a){Long uid=userId(a);service.requireActive(uid);carts.remove(uid,productId);}
 @DeleteMapping("/cart") @ResponseStatus(HttpStatus.NO_CONTENT) public void clear(Authentication a){Long uid=userId(a);service.requireActive(uid);carts.clear(uid);}
 @PostMapping("/orders/checkout") @ResponseStatus(HttpStatus.CREATED) public OrderResponse checkout(@RequestHeader("Idempotency-Key") String key,Authentication a){return service.checkoutCart(userId(a),key);}
 /** Backwards-compatible single-item order submission; userId must match the JWT subject. */
 @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) public OrderResponse legacyOrder(@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody OrderRequest r,Authentication a){return service.checkoutSingle(userId(a),key==null?java.util.UUID.randomUUID().toString():key,r);}
 @GetMapping("/orders") public OrderPageResponse list(@RequestParam(required=false)OrderStatus status,@RequestParam(required=false)Long userId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication a){Page<OrderResponse> p=service.list(userId(a),admin(a),status,userId,page,size);return new OrderPageResponse(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());}
 @GetMapping("/orders/{id}") public OrderResponse get(@PathVariable Long id,Authentication a){return service.get(id,userId(a),admin(a));}
 @PatchMapping("/orders/{id}/cancel") public OrderResponse cancel(@PathVariable Long id,Authentication a){Long uid=userId(a);OrderResponse order=service.get(id,uid,admin(a));if(!admin(a)&&!order.userId().equals(uid))throw new AccessDeniedException("Order belongs to another account");return service.transition(id,OrderStatus.CANCELLED);}
 @PatchMapping("/orders/{id}/status") public OrderResponse status(@PathVariable Long id,@Valid @RequestBody OrderStatusRequest r,Authentication a){requireAdmin(a);return service.transition(id,r.status());}
 @GetMapping("/admin/dashboard") public DashboardResponse dashboard(Authentication a){requireAdmin(a);return service.dashboard(userId(a));}
 private Long userId(Authentication a){if(!(a instanceof JwtAuthenticationToken jwt))throw new AccessDeniedException("JWT required");Object sub=jwt.getToken().getClaims().get("sub");try{return Long.valueOf(sub.toString());}catch(Exception e){throw new AccessDeniedException("JWT subject is invalid");}}
 private boolean admin(Authentication a){return a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN"));}
 private void requireAdmin(Authentication a){if(!admin(a))throw new AccessDeniedException("Administrator role required");}
}
