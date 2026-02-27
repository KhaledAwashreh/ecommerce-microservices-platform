package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.InventoryDto;
import com.kawashreh.ecommerce.product_service.application.mapper.InventoryHttpMapper;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/product-variation/{productVariationId}")
    public ResponseEntity<InventoryDto> findByProductVariationId(@PathVariable UUID productVariationId) {
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        if (inventory == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(InventoryHttpMapper.toDto(inventory));
    }

    @GetMapping("/product-variation/{productVariationId}/availability")
    public ResponseEntity<Boolean> checkAvailability(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean available = inventoryService.checkAvailability(productVariationId, quantity);
        return ResponseEntity.ok(available);
    }

    @PutMapping("/product-variation/{productVariationId}/deduct")
    public ResponseEntity<Boolean> deductStock(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean success = inventoryService.deductStock(productVariationId, quantity);
        return ResponseEntity.ok(success);
    }

    @PutMapping("/product-variation/{productVariationId}/restore")
    public ResponseEntity<Boolean> restoreStock(
            @PathVariable UUID productVariationId,
            @RequestParam int quantity) {
        boolean success = inventoryService.restoreStock(productVariationId, quantity);
        return ResponseEntity.ok(success);
    }
}
