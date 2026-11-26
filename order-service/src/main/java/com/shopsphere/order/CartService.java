package com.shopsphere.order;
import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.server.ResponseStatusException;import java.util.List;
@Service public class CartService {
 private final CartItemRepository cart;private final ProductClient products;
 public CartService(CartItemRepository cart,ProductClient products){this.cart=cart;this.products=products;}
 @Transactional public CartItem add(Long user,Long product,int qty){ProductDto p=products.findProduct(product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));if(!p.available())throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is unavailable");
  CartItem item=cart.findByUserIdAndProductId(user,product).orElseGet(()->new CartItem(user,product,0));int total=item.getQuantity()+qty;if(total>100||total>p.stock())throw new ResponseStatusException(HttpStatus.CONFLICT,"Requested quantity exceeds available stock");item.setQuantity(total);return cart.save(item);}
 @Transactional public CartItem set(Long user,Long product,int qty){ProductDto p=products.findProduct(product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));if(!p.available()||qty>p.stock())throw new ResponseStatusException(HttpStatus.CONFLICT,"Requested quantity exceeds available stock");CartItem i=cart.findByUserIdAndProductId(user,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cart item not found"));i.setQuantity(qty);return i;}
 @Transactional(readOnly=true) public List<CartItem> items(Long user){return cart.findByUserId(user);}
 @Transactional public void remove(Long user,Long product){cart.findByUserIdAndProductId(user,product).ifPresent(cart::delete);}
 @Transactional public void clear(Long user){cart.deleteByUserId(user);}
}
