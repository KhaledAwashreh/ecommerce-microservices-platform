package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import io.github.resilience4j.retry.annotation.Retry;
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

    // GH #63: resilience4j.retry.instances.product-service (application.yml) was
    // configured but never actually wired to anything - no @Retry annotation or manual
    // Resilience4j retry usage existed anywhere in order-service, so calls never retried
    // despite the config implying otherwise. Wired here (rather than removed) because
    // these two calls are specifically the ones GH #30 made safe to retry: product-service
    // keys its deduction ledger on orderItemId, so a repeated deduct/restore for the same
    // order item is a no-op, not a double-deduction/over-restore. retryExceptions in
    // application.yml restricts retries to ProductServiceUnavailableException (503) and
    // low-level I/O failures - not to 404/400, which are permanent outcomes retrying can't
    // fix. retrieveProduct/retrieveInventory/checkInventoryAvailability are left
    // unannotated: they're plain reads with no idempotency concern either way, and this
    // fix is scoped to the calls GH #30's ledger was actually built to protect.
    @Retry(name = "product-service")
    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_DEDUCT)
    Boolean deductInventory(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity);

    @Retry(name = "product-service")
    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_RESTORE)
    Boolean restoreInventory(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity);
}
