package com.kawashreh.ecommerce.order_service.application.dto;

import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
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

    // GH #58: shipping address selected at checkout. Not @NonNull - unlike buyer/seller/
    // storeId, this is a new optional field and existing callers other than the checkout
    // flow don't populate it.
    private UUID shippingAddressId;

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
