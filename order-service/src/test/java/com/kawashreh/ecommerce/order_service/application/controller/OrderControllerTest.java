package com.kawashreh.ecommerce.order_service.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.order_service.application.dto.OrderDto;
import com.kawashreh.ecommerce.order_service.application.dto.OrderItemDto;
import com.kawashreh.ecommerce.order_service.domain.exception.InvalidOrderStateException;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import com.kawashreh.ecommerce.order_service.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for GH #40: OrderController had no {@code @Valid} anywhere, so
 * null/negative/malformed order payloads (missing buyer, an order with zero items, a
 * negative item quantity) reached OrderService and the database unchecked.
 *
 * <p>Negative-case payloads are built as raw {@code Map -> JSON} rather than via
 * {@code OrderDto.builder()}: OrderDto's fields are Lombok {@code @NonNull}, so the
 * generated builder/all-args constructor itself throws NPE for an intentionally
 * missing field before the request would ever be sent - a real malformed HTTP body
 * (field omitted from the JSON) does not hit that constructor path at all, since
 * Jackson binds via the no-args constructor and setters.
 */
// excludeFilters: web-layer slice test, unrelated to JwtAuthFilter (GH #17) - see
// CartControllerTest in this package for the same rationale.
@WebMvcTest(controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private Map<String, Object> validItemMap() {
        Map<String, Object> item = new HashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("productSku", UUID.randomUUID().toString());
        item.put("quantity", 2);
        item.put("unitPrice", BigDecimal.TEN);
        item.put("createdAt", Instant.now().toString());
        item.put("updatedAt", Instant.now().toString());
        return item;
    }

    private Map<String, Object> validOrderMap() {
        Map<String, Object> order = new HashMap<>();
        order.put("storeId", UUID.randomUUID().toString());
        order.put("seller", UUID.randomUUID().toString());
        order.put("buyer", UUID.randomUUID().toString());
        order.put("selectedItems", List.of(validItemMap()));
        order.put("createdAt", Instant.now().toString());
        order.put("updatedAt", Instant.now().toString());
        return order;
    }

    @Test
    void createOrder_shouldRejectMissingBuyer_withoutCallingService() throws Exception {
        Map<String, Object> order = validOrderMap();
        order.remove("buyer");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_shouldRejectEmptyItemList_withoutCallingService() throws Exception {
        Map<String, Object> order = validOrderMap();
        order.put("selectedItems", List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_shouldRejectNegativeQuantity_withoutCallingService() throws Exception {
        Map<String, Object> item = validItemMap();
        item.put("quantity", -1);
        Map<String, Object> order = validOrderMap();
        order.put("selectedItems", List.of(item));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_shouldCallService_whenPayloadValid() throws Exception {
        OrderDto orderDto = OrderDto.builder()
                .id(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .seller(UUID.randomUUID())
                .buyer(UUID.randomUUID())
                .selectedItems(List.of(OrderItemDto.builder()
                        .id(UUID.randomUUID())
                        .productSku(UUID.randomUUID())
                        .quantity(2)
                        .unitPrice(BigDecimal.TEN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(orderService.create(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isCreated());

        verify(orderService).create(any(Order.class));
    }

    @Test
    void updateOrder_shouldReturnConflict_whenServiceRejectsTheStatusTransition() throws Exception {
        // GH #43: OrderServiceImpl.update throws InvalidOrderStateException for an illegal
        // status transition; the controller must map that to 409, not let it fall through
        // to a 500.
        UUID orderId = UUID.randomUUID();
        OrderDto orderDto = OrderDto.builder()
                .id(orderId)
                .storeId(UUID.randomUUID())
                .seller(UUID.randomUUID())
                .buyer(UUID.randomUUID())
                .selectedItems(List.of(OrderItemDto.builder()
                        .id(UUID.randomUUID())
                        .productSku(UUID.randomUUID())
                        .quantity(2)
                        .unitPrice(BigDecimal.TEN)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(orderService.update(any(Order.class)))
                .willThrow(new InvalidOrderStateException("Cannot transition order status from CONFIRMED to PENDING"));

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isConflict());
    }
}
