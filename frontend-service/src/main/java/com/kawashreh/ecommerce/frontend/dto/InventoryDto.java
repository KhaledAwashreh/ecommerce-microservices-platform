package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDto {
    private UUID id;
    private UUID productVariationId;
    private int quantity;
    private int reservedQuantity;
    private String warehouseLocation;
    private Instant createdAt;
    private Instant updatedAt;

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }
}
