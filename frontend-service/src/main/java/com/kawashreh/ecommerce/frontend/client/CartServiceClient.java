package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.CartDto;
import com.kawashreh.ecommerce.frontend.dto.CartItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Feign client for the order-service cart API.
 * Routed through the API gateway, like every other client in this module.
 */
@FeignClient(name = "cart-service-UI-client", url = "${api.gateway.base-url}/api/v1/carts")
public interface CartServiceClient {

    @GetMapping("/user/{userId}")
    CartDto getCartForUser(@PathVariable("userId") UUID userId);

    @PostMapping("/user/{userId}/items")
    CartDto addItem(@PathVariable("userId") UUID userId, @RequestBody CartItemDto item);

    @DeleteMapping("/user/{userId}/items/{itemId}")
    CartDto removeItem(@PathVariable("userId") UUID userId, @PathVariable("itemId") UUID itemId);
}
