package com.shopsphere.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository repository;
    private final UserClient userClient;
    private final ProductClient productClient;

    public OrderController(OrderRepository repository, UserClient userClient, ProductClient productClient) {
        this.repository = repository;
        this.userClient = userClient;
        this.productClient = productClient;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest request) {
        // Service-to-service calls (no database transaction is held open while we wait).
        if (!userClient.exists(request.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User " + request.userId() + " does not exist");
        }
        ProductDto product = productClient.findProduct(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product " + request.productId() + " does not exist"));
        if (product.price() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "product-service returned no price");
        }

        BigDecimal total = product.price().multiply(BigDecimal.valueOf(request.quantity().longValue()));
        Order saved = repository.save(new Order(
                request.userId(), request.productId(), request.quantity(), product.price(), total));
        return OrderResponse.from(saved);
    }

    @GetMapping
    public List<OrderResponse> list() {
        return repository.findAll().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable("id") Long id) {
        return repository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }
}
