package com.shopsphere.product;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@RestController @RequestMapping("/internal/products")
public class InternalStockController {
 private final ProductService products;private final byte[] serviceKey;
 public InternalStockController(ProductService products,@Value("${shopsphere.service-api-key:}")String key){this.products=products;this.serviceKey=key.getBytes(StandardCharsets.UTF_8);}
 @PostMapping("/{id}/reservations") public ReservationResponse reserve(@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Service-Token",required=false)String token,@Valid @RequestBody ReservationRequest r){authorize(token);return products.reserve(id,r.quantity(),key);}
 @PostMapping("/reservations/{key}/commit") public ReservationResponse commit(@PathVariable String key,@RequestHeader(value="X-Service-Token",required=false)String token){authorize(token);return products.settle(key,true);}
 @PostMapping("/reservations/{key}/release") public ReservationResponse release(@PathVariable String key,@RequestHeader(value="X-Service-Token",required=false)String token){authorize(token);return products.settle(key,false);}
 private void authorize(String token){if(serviceKey.length<32||token==null||!MessageDigest.isEqual(serviceKey,token.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid service credentials");}
}
