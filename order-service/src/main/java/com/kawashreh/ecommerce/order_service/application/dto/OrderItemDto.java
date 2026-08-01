package com.kawashreh.ecommerce.order_service.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotNull
    private UUID productSku;

    // GH #40: negative/zero quantities reached the service/DB unchecked.
    @Positive
    private int quantity;

    @NotNull
    @Positive
    private BigDecimal unitPrice;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
