package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.InventoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.UUID;

/**
 * Feign client for Inventory Service.
 * Uses API Gateway for service discovery.
 */
@FeignClient(name = "inventory-service-UI-client", url = "${api.gateway.base-url}/api/v1/inventory")
public interface InventoryServiceClient {

    @GetMapping("/product-variation/{productVariationId}")
    InventoryDto getInventoryByVariation(@PathVariable("productVariationId") UUID productVariationId);

    @GetMapping("/product-variation/{productVariationId}/availability")
    Boolean checkAvailability(@PathVariable("productVariationId") UUID productVariationId,
                               @RequestParam int quantity);

    @PutMapping("/product-variation/{productVariationId}/deduct")
    Boolean deductStock(@PathVariable("productVariationId") UUID productVariationId,
                            @RequestParam int quantity);

    @PutMapping("/product-variation/{productVariationId}/restore")
    Boolean restoreStock(@PathVariable("productVariationId") UUID productVariationId,
                             @RequestParam int quantity);
}