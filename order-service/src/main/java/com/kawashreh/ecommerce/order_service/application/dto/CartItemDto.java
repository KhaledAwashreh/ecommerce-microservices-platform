package com.kawashreh.ecommerce.order_service.application.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CartItemDto {

    @NonNull
    private UUID id;

    @NonNull
    private UUID productId;

    private UUID productVariantId;

    @NonNull
    private UUID storeId;

    @NonNull
    private String productSku;

    @NonNull
    private String productName;

    @NonNull
    private int quantity;

    @NonNull
    private BigDecimal unitPrice;

    @NonNull
    private BigDecimal lineTotal;

    @NonNull
    private String currency;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;
}
