package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Order Service.
 * Uses API Gateway for service discovery.
 */
@FeignClient(name = "order-service-UI-client", url = "${api.gateway.base-url}/api/v1/orders")
public interface OrderServiceClient {

    @PostMapping
    OrderDto createOrder(@RequestBody OrderDto order);

    @GetMapping
    List<OrderDto> getAllOrders();

    @GetMapping("/{id}")
    OrderDto getOrderById(@PathVariable UUID id);

    @GetMapping("/buyer/{buyerId}")
    List<OrderDto> getOrdersByBuyer(@PathVariable("buyerId") UUID buyerId);
}