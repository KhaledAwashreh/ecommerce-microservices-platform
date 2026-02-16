package com.order_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class CartItem {

    private UUID id;
    private UUID cartId;

    private UUID productId;
    private UUID productVariantId;
    private UUID storeId;

    private String productSku;
    private String productName;

    private int quantity;

    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String currency;

    private Instant createdAt;
    private Instant updatedAt;
}
