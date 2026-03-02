package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.InventoryDto;
import com.kawashreh.ecommerce.product_service.application.mapper.InventoryHttpMapper;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.product_service.constants.ApiPaths;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.INVENTORY_BASE)
@RequestMapping("/api/v1/inventory")
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

    @PutMapping(ApiPaths.PRODUCT_VARIATION_RESTORE)
    public ResponseEntity<Boolean> restoreStock(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean success = inventoryService.restoreStock(productVariationId, quantity);
        return ResponseEntity.ok(success);
    }
}
