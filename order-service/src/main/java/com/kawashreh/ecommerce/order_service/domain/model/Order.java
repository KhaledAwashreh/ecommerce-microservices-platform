package com.kawashreh.ecommerce.order_service.domain.model;

import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Order {

    private UUID id;

    private UUID storeId;

    private UUID seller;

    private UUID buyer;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    @Setter(AccessLevel.NONE)  // prevent Lombok from generating a setter for this field
    private List<OrderItem> selectedItems = new ArrayList<>();

    @Builder.Default
    @Setter(AccessLevel.NONE)
    private List<Discount> discountsApplied = new ArrayList<>();

    public Order setDiscountsApplied(List<Discount> discounts) {
        this.discountsApplied = discounts != null ? new ArrayList<>(discounts) : new ArrayList<>();
        return this;
    }

    private Instant createdAt;
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;


    public Order setSelectedItems(List<OrderItem> items) {
        this.selectedItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        return this;
    }
}

