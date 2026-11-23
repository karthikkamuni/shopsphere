package com.shopsphere.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calls user-service over the cluster network. The base URL comes from the
 * SERVICES_USER_URL environment variable (property services.user.url); in
 * Kubernetes that is the Service DNS name http://user-service:8081.
 */
@Component
public class UserClient {

    private final RestClient client;

    public UserClient(RestClient.Builder builder, @Value("${services.user.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public boolean exists(Long userId) {
        try {
            client.get().uri("/api/users/{id}", userId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service is unavailable");
        }
    }
}
