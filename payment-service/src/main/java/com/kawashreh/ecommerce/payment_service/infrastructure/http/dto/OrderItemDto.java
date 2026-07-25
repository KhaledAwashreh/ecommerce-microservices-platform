package com.kawashreh.ecommerce.payment_service.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign response shape for an order-service order item. Mirrors only the fields
 * payment-service needs (quantity, unitPrice) to re-derive the authoritative payment
 * amount; not a full mapping of order-service's OrderItemDto.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDto {

    private UUID id;

    private UUID productSku;

    private int quantity;

    private BigDecimal unitPrice;
}
