package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.InventoryDto;
import com.kawashreh.ecommerce.product_service.application.mapper.InventoryHttpMapper;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.product_service.constants.ApiPaths;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.INVENTORY_BASE)
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping(ApiPaths.PRODUCT_VARIATION)
    public ResponseEntity<InventoryDto> findByProductVariationId(@PathVariable UUID productVariationId) {
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        if (inventory == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(InventoryHttpMapper.toDto(inventory));
    }

    @GetMapping(ApiPaths.PRODUCT_VARIATION_AVAILABILITY)
    public ResponseEntity<Boolean> checkAvailability(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean available = inventoryService.checkAvailability(productVariationId, quantity);
        return ResponseEntity.ok(available);
    }

    @PutMapping(ApiPaths.PRODUCT_VARIATION_DEDUCT)
    public ResponseEntity<Boolean> deductStock(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean success = inventoryService.deductStock(productVariationId, quantity);
        return ResponseEntity.ok(success);
    }

    // GH #30: restoreStock is now lock-protected and rejects non-positive quantities, but
    // has no ceiling on how much can be restored, and this endpoint has no caller
    // restriction - a caller could still inflate stock past what was ever deducted. A real
    // ceiling needs a deducted-quantity ledger this module doesn't have; the sole current
    // caller (order-service's restoreDeductedInventory) only ever restores exactly what it
    // previously deducted, so this is a trust-boundary gap, not an active bug, but it's
    // real and unaddressed - same class of issue as the other "no ownership/role check"
    // gotchas already documented for this module's controllers.
    @PutMapping(ApiPaths.PRODUCT_VARIATION_RESTORE)
    public ResponseEntity<Boolean> restoreStock(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean success = inventoryService.restoreStock(productVariationId, quantity);
        return ResponseEntity.ok(success);
    }
}
