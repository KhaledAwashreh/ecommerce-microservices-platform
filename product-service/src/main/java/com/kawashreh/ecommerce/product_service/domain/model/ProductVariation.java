package com.kawashreh.ecommerce.product_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariation {

    private UUID id;

    private String sku;

    private int stockQuantity;

    private String name;

    private BigDecimal price;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;

    private String thumbnailUrl;

    private List<UUID> attachments;

    private List<Attribute> attributes;

    private Product product;
}

