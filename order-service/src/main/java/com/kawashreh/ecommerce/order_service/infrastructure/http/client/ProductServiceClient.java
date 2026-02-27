package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping("/api/v1/product/{productId}")
    ProductDto retrieveProduct(@PathVariable UUID productId);

    @GetMapping("/api/v1/inventory/product-variation/{productVariationId}")
    InventoryDto retrieveInventory(@PathVariable UUID productVariationId);

    @GetMapping("/api/v1/inventory/product-variation/{productVariationId}/availability")
    Boolean checkInventoryAvailability(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);

    @PutMapping("/api/v1/inventory/product-variation/{productVariationId}/deduct")
    Boolean deductInventory(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);

    @PutMapping("/api/v1/inventory/product-variation/{productVariationId}/restore")
    Boolean restoreInventory(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);
}
