package com.kawashreh.ecommerce.order_service.application.dto;

import com.kawashreh.ecommerce.order_service.domain.enums.CartStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CartDto {

    @NonNull
    private UUID id;

    @NonNull
    private UUID userId;

    private UUID sessionId;

    @NonNull
    @Builder.Default
    private CartStatus status = CartStatus.ACTIVE;

    @Builder.Default
    private List<CartItemDto> cartItems = new ArrayList<>();

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal shippingTotal;
    private BigDecimal totalPrice;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
