package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Inventory;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public Inventory findByProductVariationId(UUID productVariationId) {
        return inventoryRepository.findByProductVariationId(productVariationId)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public boolean checkAvailability(UUID productVariationId, int quantity) {
        return inventoryRepository.findByProductVariationId(productVariationId)
                .map(inventory -> inventory.getQuantity() >= quantity)
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean deductStock(UUID productVariationId, int quantity) {
        // Acquire pessimistic lock first (SELECT ... FOR UPDATE)
        var inventoryOpt = inventoryRepository.findByProductVariationIdWithLock(productVariationId);
        
        if (inventoryOpt.isEmpty()) {
            logger.warn("Inventory not found for variation: {}", productVariationId);
            return false;
        }
        
        // Atomic UPDATE with WHERE condition (protected by lock)
        int updated = inventoryRepository.deductQuantity(productVariationId, quantity);
        
        if (updated > 0) {
            logger.info("Deducted {} units from inventory for variation {}", quantity, productVariationId);
            return true;
        }
        
        // If update failed, it's due to insufficient stock
        var inventory = inventoryOpt.get();
        logger.warn("Insufficient stock for variation {}: requested {}, available {}",
                productVariationId, quantity, inventory.getQuantity());
        return false;
    }

    @Override
    @Transactional
    public boolean restoreStock(UUID productVariationId, int quantity) {
        int updated = inventoryRepository.restoreQuantity(productVariationId, quantity);
        if (updated > 0) {
            logger.info("Restored {} units to inventory for variation {}", quantity, productVariationId);
            return true;
        }
        logger.warn("Failed to restore stock for variation {}: inventory not found", productVariationId);
        return false;
    }

    private Inventory toDomain(InventoryEntity entity) {
        return Inventory.builder()
                .id(entity.getId())
                .productVariationId(entity.getProductVariation().getId())
                .quantity(entity.getQuantity())
                .reservedQuantity(entity.getReservedQuantity())
                .warehouseLocation(entity.getWarehouseLocation())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
