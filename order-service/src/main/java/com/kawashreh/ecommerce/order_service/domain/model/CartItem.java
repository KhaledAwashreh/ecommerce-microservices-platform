package com.kawashreh.ecommerce.order_service.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CartItem {

    private UUID id;
    private UUID cartId;

    private UUID productId;
    private UUID productVariantId;

    private String productSku;
    private String productName;

    private int quantity;

    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String currency;

    private Instant createdAt;
    private Instant updatedAt;
}
