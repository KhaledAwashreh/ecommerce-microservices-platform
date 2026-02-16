package com.order_service.application.dto;

import com.order_service.domain.enums.OrderStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    @NonNull
    private UUID id;

    @NonNull
    private UUID storeId;

    @NonNull
    private UUID seller;

    @NonNull
    private UUID buyer;

    @NonNull
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    private List<OrderItemDto> selectedItems = new ArrayList<>();

    @Builder.Default
    private List<DiscountDto> discountsApplied = new ArrayList<>();

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
