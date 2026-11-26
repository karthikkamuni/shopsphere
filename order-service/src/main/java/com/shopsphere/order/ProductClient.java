package com.shopsphere.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import org.springframework.http.MediaType;

/**
 * Calls product-service over the cluster network. The base URL comes from the
 * SERVICES_PRODUCT_URL environment variable (property services.product.url); in
 * Kubernetes that is the Service DNS name http://product-service:8082.
 */
@Component
public class ProductClient {

    private final RestClient client;
    private final String serviceToken;

    public ProductClient(RestClient.Builder builder, @Value("${services.product.url}") String baseUrl,@Value("${shopsphere.service-api-key:}")String serviceToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken=serviceToken;
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
    public ReservationResponse reserve(Long id,int quantity,String key){
        try{return client.post().uri("/internal/products/{id}/reservations",id).header("X-Service-Token",serviceToken).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of("quantity",quantity)).retrieve().body(ReservationResponse.class);}
        catch(RestClientResponseException e){HttpStatus status=e.getStatusCode().value()==404?HttpStatus.NOT_FOUND:e.getStatusCode().value()==409?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE;throw new ResponseStatusException(status,status==HttpStatus.CONFLICT?"Product stock could not be reserved":"product-service reservation request failed");}
        catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"product-service is unavailable");}
    }
    public void commit(String key){settle(key,"commit");}
    public void release(String key){settle(key,"release");}
    private void settle(String key,String action){try{client.post().uri("/internal/products/reservations/{key}/{action}",key,action).header("X-Service-Token",serviceToken).retrieve().toBodilessEntity();}
        catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product reservation settlement failed");}}
}
