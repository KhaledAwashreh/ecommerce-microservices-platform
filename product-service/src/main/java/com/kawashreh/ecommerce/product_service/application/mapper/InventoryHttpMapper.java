package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.InventoryDto;
import com.kawashreh.ecommerce.product_service.domain.model.Inventory;

public final class InventoryHttpMapper {

    private InventoryHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static InventoryDto toDto(Inventory inventory) {
        if (inventory == null) return null;

        return InventoryDto.builder()
                .id(inventory.getId())
                .productVariationId(inventory.getProductVariationId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .warehouseLocation(inventory.getWarehouseLocation())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    // DTO -> Domain
    public static Inventory toDomain(InventoryDto dto) {
        if (dto == null) return null;

        return Inventory.builder()
                .id(dto.getId())
                .productVariationId(dto.getProductVariationId())
                .quantity(dto.getQuantity())
                .reservedQuantity(dto.getReservedQuantity())
                .warehouseLocation(dto.getWarehouseLocation())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
