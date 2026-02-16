package com.order_service.domain.model;

import com.order_service.domain.enums.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Component
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Cart {

    private UUID id;
    private UUID userId;
    private UUID createdBy;
    private UUID updatedBy;
    private UUID sessionId;
    private CartStatus status;

    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();

    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal shippingTotal;
    private BigDecimal totalPrice;

    private Instant createdAt;
    private Instant updatedAt;
}
