package com.order_service.domain.model;

import com.order_service.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

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
public class Order {

    private UUID id;

    private UUID storeId;

    private UUID seller;

    private UUID buyer;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    private List<OrderItem> selectedItems = new ArrayList<>();

    @Builder.Default
    private List<Discount> discountsApplied = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

}
