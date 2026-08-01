package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryDto {

    private UUID id;

    private UUID productVariationId;

    private int quantity;

    private String warehouseLocation;

    private Instant createdAt;

    private Instant updatedAt;
}
