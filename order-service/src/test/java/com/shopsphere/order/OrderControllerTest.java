package com.shopsphere.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real clients would call other services over HTTP; tests replace them.
    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private ProductClient productClient;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void createOrderCalculatesTotal() throws Exception {
        when(userClient.exists(1L)).thenReturn(true);
        when(productClient.findProduct(5L))
                .thenReturn(Optional.of(new ProductDto(5L, "Mechanical Keyboard", new BigDecimal("49.99"))));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"productId\":5,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalPrice").value(99.98));
    }

    @Test
    void unknownUserReturns400() throws Exception {
        when(userClient.exists(404L)).thenReturn(false);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":404,\"productId\":5,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductReturns400() throws Exception {
        when(userClient.exists(2L)).thenReturn(true);
        when(productClient.findProduct(404L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2,\"productId\":404,\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downstreamOutageReturns503() throws Exception {
        when(userClient.exists(3L))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user-service is unavailable"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":3,\"productId\":5,\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void zeroQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"productId\":5,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound());
    }
}
