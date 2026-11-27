package com.shopsphere.product;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/products")
public class ProductController {
 private final ProductService service; private final ProductRepository products;
 public ProductController(ProductService service,ProductRepository products){this.service=service;this.products=products;}
 @GetMapping public List<ProductResponse> list(@RequestParam(required=false) String q,@RequestParam(required=false) String category,@RequestParam(defaultValue="name,asc") String sort){return service.browse(q,category,sort);}
 @GetMapping("/admin/inactive") @PreAuthorize("hasRole('ADMIN')") public List<ProductResponse> inactive(){return service.inactive();}
 @GetMapping("/page") public ProductPageResponse page(@RequestParam(required=false) String q,@RequestParam(required=false) String category,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="name,asc") String sort){Page<ProductResponse> result=service.page(q,category,page,size,sort);return new ProductPageResponse(result.getContent(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());}
 @GetMapping("/{id}") public ProductResponse get(@PathVariable Long id){return service.get(id);}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED) public ProductResponse create(@Valid @RequestBody ProductRequest r){return service.create(r);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ProductResponse update(@PathVariable Long id,@Valid @RequestBody ProductRequest r){return service.update(id,r);}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.OK) public ProductResponse delete(@PathVariable Long id){return service.deactivate(id);}
 @PatchMapping("/{id}/reactivate") @PreAuthorize("hasRole('ADMIN')") public ProductResponse reactivate(@PathVariable Long id){return service.reactivate(id);}
 @PatchMapping("/{id}/stock") @PreAuthorize("hasRole('ADMIN')") public ProductResponse stock(@PathVariable Long id,@Valid @RequestBody StockRequest r){return service.stock(id,r.quantity());}
 @PatchMapping("/{id}/availability") @PreAuthorize("hasRole('ADMIN')") public ProductResponse availability(@PathVariable Long id,@RequestBody @Valid AvailabilityRequest r){return service.availability(id,r.available());}
 @GetMapping("/admin/low-stock") @PreAuthorize("hasRole('ADMIN')") public List<ProductResponse> lowStock(@RequestParam(defaultValue="5") int threshold){return products.findByActiveTrue().stream().filter(p->p.getStock()<=threshold).map(ProductResponse::from).toList();}
}
