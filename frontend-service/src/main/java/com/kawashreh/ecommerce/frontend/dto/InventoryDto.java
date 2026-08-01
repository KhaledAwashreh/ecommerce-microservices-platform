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
    private String warehouseLocation;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isAvailable(int requestedQuantity) {
        return quantity >= requestedQuantity;
    }
}
