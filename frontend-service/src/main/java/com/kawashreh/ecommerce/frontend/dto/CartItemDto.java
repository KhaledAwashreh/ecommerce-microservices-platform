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
public class CartItemDto {
    private UUID id;
    private UUID productId;
    private UUID productVariationId;
    private String productName;
    private String variationName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String thumbnailUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
