package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.CartDto;
import com.kawashreh.ecommerce.frontend.dto.CartItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Cart Service.
 * Uses API Gateway - cart is managed by order-service.
 */
@FeignClient(name = "cart-service-UI-client", url = "${api.gateway.base-url}/api/v1/cart")
public interface CartServiceClient {

    @GetMapping
    CartDto getCartByUserId(@RequestParam("userId") UUID userId);

    @GetMapping("/{cartId}")
    CartDto getCartById(@PathVariable("cartId") UUID cartId);

    @PostMapping("/add")
    CartDto addToCart(@RequestParam("userId") UUID userId, @RequestBody CartItemDto item);

    @PostMapping("/{cartId}/remove")
    CartDto removeFromCart(@PathVariable("cartId") UUID cartId, @RequestParam("productVariationId") UUID productVariationId);

    @PostMapping("/{cartId}/update")
    CartDto updateQuantity(@PathVariable("cartId") UUID cartId, @RequestParam("productVariationId") UUID productVariationId, @RequestParam int quantity);

    @PostMapping("/{cartId}/clear")
    CartDto clearCart(@PathVariable("cartId") UUID cartId);
}