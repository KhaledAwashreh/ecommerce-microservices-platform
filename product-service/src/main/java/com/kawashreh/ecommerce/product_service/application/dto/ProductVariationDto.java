package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariationDto {

    private UUID id;

    private UUID productId;

    private String sku;

    private String name;

    private BigDecimal price;

    private int stockQuantity;

    private Boolean isActive;

    private String thumbnailUrl;

    private Instant createdAt;

    private Instant updatedAt;

}
