package com.kawashreh.ecommerce.payment_service.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Feign response shape for an order-service order. Mirrors only the fields
 * payment-service needs to re-derive the authoritative payment amount; not a full
 * mapping of order-service's OrderDto (see order-service's application/dto/OrderDto.java).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    private UUID id;

    private UUID buyer;

    private List<OrderItemDto> selectedItems;
}
