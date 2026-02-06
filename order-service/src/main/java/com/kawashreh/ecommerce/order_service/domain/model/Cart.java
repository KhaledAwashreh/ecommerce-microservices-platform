package com.kawashreh.ecommerce.order_service.domain.model;

import com.kawashreh.ecommerce.order_service.domain.enums.CartStatus;
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
    private UUID sessionId; //when moving between guest to actual user
    private CartStatus status;

    private List<CartItem> cartItems = new ArrayList<>();
    BigDecimal subtotal;
    BigDecimal discountTotal;
    BigDecimal taxTotal;
    BigDecimal shippingTotal;
    BigDecimal totalPrice;

    private Instant createdAt;
    private Instant updatedAt;
}
