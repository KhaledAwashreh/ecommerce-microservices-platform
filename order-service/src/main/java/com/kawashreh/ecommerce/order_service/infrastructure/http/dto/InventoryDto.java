package com.kawashreh.ecommerce.order_service.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
