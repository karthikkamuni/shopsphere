package com.shopsphere.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Calls product-service over the cluster network. The base URL comes from the
 * SERVICES_PRODUCT_URL environment variable (property services.product.url); in
 * Kubernetes that is the Service DNS name http://product-service:8082.
 */
@Component
public class ProductClient {

    private final RestClient client;

    public ProductClient(RestClient.Builder builder, @Value("${services.product.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public Optional<ProductDto> findProduct(Long productId) {
        try {
            return Optional.ofNullable(
                    client.get().uri("/api/products/{id}", productId).retrieve().body(ProductDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "product-service is unavailable");
        }
    }
}
