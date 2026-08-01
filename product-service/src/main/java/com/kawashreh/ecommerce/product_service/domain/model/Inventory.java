package com.kawashreh.ecommerce.product_service.domain.model;

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
public class Inventory {

    private UUID id;

    private UUID productVariationId;

    private int quantity;

    private String warehouseLocation;

    private Instant createdAt;

    private Instant updatedAt;
}
