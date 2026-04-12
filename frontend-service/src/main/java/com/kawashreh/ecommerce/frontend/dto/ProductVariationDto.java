package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariationDto {
    private UUID id;
    private UUID productId;
    private String sku;
    private String name;
    private BigDecimal price;
    private int stockQuantity;
    private boolean active;
    private String thumbnailUrl;
    private Instant createdAt;
    private Instant updatedAt;
}