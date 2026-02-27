package com.kawashreh.ecommerce.product_service.dataAccess.mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Inventory;

public final class InventoryMapper {

    private InventoryMapper() {} // Prevent instantiation

    // Entity -> Domain
    public static Inventory toDomain(InventoryEntity entity) {
        if (entity == null) return null;

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

    // Domain -> Entity
    public static InventoryEntity toEntity(Inventory inventory) {
        if (inventory == null) return null;

        return InventoryEntity.builder()
                .id(inventory.getId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .warehouseLocation(inventory.getWarehouseLocation())
                .build();
    }
}
