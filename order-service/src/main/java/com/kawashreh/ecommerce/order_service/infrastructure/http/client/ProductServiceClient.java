package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;

import java.util.UUID;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping(ApiPaths.PRODUCT_BASE + ApiPaths.PRODUCT_BY_ID)
    ProductDto retrieveProduct(@PathVariable UUID productId);

    @GetMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_BY_VARIATION)
    InventoryDto retrieveInventory(@PathVariable UUID productVariationId);

    @GetMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_AVAILABILITY)
    Boolean checkInventoryAvailability(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);

    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_DEDUCT)
    Boolean deductInventory(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);

    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_RESTORE)
    Boolean restoreInventory(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity);
}
