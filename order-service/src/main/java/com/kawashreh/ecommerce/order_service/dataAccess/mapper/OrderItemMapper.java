package com.kawashreh.ecommerce.order_service.dataAccess.mapper;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderItemEntity;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class OrderItemMapper {

    public static OrderItemEntity toEntity(OrderItem d) {
        if (d == null) return null;
        return OrderItemEntity.builder()
                .id(d.getId())
                .productSku(d.getProductSku())
                .quantity(d.getQuantity())
                .unitPrice(d.getUnitPrice())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .createdBy(d.getCreatedBy())
                .updatedBy(d.getUpdatedBy())
                .build();
    }

    public static OrderItem toDomain(OrderItemEntity e) {
        if (e == null) return null;
        return OrderItem.builder()
                .id(e.getId())
                .orderId(e.getOrder() != null ? e.getOrder().getId() : null)
                .productSku(e.getProductSku())
                .quantity(e.getQuantity())
                .unitPrice(e.getUnitPrice())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .createdBy(e.getCreatedBy())
                .updatedBy(e.getUpdatedBy())
                .build();
    }

    public static List<OrderItem> toDomainList(List<OrderItemEntity> list) {
        if (list == null) return new ArrayList<>();
        return list.stream()
                .map(OrderItemMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static List<OrderItemEntity> toEntityList(List<OrderItem> list) {
        if (list == null) return new ArrayList<>();
        return list.stream()
                .map(OrderItemMapper::toEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}