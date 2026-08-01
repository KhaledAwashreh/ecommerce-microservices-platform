package com.kawashreh.ecommerce.order_service.application.dto;

import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private UUID storeId;

    @NotNull
    private UUID seller;

    @NotNull
    private UUID buyer;

    // GH #58: shipping address selected at checkout. Not required - unlike buyer/seller/
    // storeId, this is a new optional field and existing callers other than the checkout
    // flow don't populate it.
    private UUID shippingAddressId;

    @NonNull
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // GH #40: an order with no items reached the service/DB unchecked.
    @NotEmpty
    @Valid
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
