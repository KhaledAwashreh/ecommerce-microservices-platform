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
public class OrderItemDto {

    @NonNull
    private UUID id;

    @NonNull
    private UUID productSku;

    @NonNull
    private int quantity;

    @NonNull
    private BigDecimal unitPrice;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
