package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.ShippingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Shipping Service.
 * Uses API Gateway for shipping/tracking functionality.
 */
@FeignClient(name = "shipping-service-UI-client", url = "${api.gateway.base-url}/api/v1/shipping")
public interface ShippingServiceClient {

    @GetMapping("/order/{orderId}")
    ShippingDto getShippingByOrderId(@PathVariable("orderId") UUID orderId);

    @GetMapping("/{shippingId}")
    ShippingDto getShippingById(@PathVariable("shippingId") UUID shippingId);

    @GetMapping("/tracking/{trackingNumber}")
    ShippingDto getShippingByTracking(@PathVariable String trackingNumber);

    @PostMapping("/{shippingId}/mark-delivered")
    ShippingDto markAsDelivered(@PathVariable("shippingId") UUID shippingId);
}