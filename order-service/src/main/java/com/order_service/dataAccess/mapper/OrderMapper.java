package com.order_service.dataAccess.mapper;

import com.order_service.dataAccess.entity.OrderEntity;
import com.order_service.domain.model.Order;

import java.util.List;

public final class OrderMapper {

    public static OrderEntity toEntity(Order d) {
        if (d == null) return null;
        return OrderEntity.builder()
                .id(d.getId())
                .storeId(d.getStoreId())
                .seller(d.getSeller())
                .buyer(d.getBuyer())
                .status(d.getStatus())
                .selectedItems(OrderItemMapper.toEntityList(d.getSelectedItems()))
                .discountsApplied(DiscountMapper.toEntityList(d.getDiscountsApplied()))
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .createdBy(d.getCreatedBy())
                .updatedBy(d.getUpdatedBy())
                .build();
    }

    public static Order toDomain(OrderEntity e) {
        if (e == null) return null;
        return Order.builder()
                .id(e.getId())
                .storeId(e.getStoreId())
                .seller(e.getSeller())
                .buyer(e.getBuyer())
                .status(e.getStatus())
                .selectedItems(OrderItemMapper.toDomainList(e.getSelectedItems()))
                .discountsApplied(DiscountMapper.toDomainList(e.getDiscountsApplied()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .createdBy(e.getCreatedBy())
                .updatedBy(e.getUpdatedBy())
                .build();
    }

    public static List<Order> toDomainList(List<OrderEntity> list) {
        if (list == null) return null;
        return list.stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    public static List<OrderEntity> toEntityList(List<Order> list) {
        if (list == null) return null;
        return list.stream()
                .map(OrderMapper::toEntity)
                .toList();
    }
}
