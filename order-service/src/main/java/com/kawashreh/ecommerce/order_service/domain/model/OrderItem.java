package com.kawashreh.ecommerce.order_service.domain.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Component
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class OrderItem {

    private UUID id;
    private UUID orderId;
    private UUID productSku;
    private int quantity;

    private BigDecimal unitPrice;

    private Instant createdAt;
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

}
