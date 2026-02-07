package com.kawashreh.ecommerce.order_service.dataAccess.mapper;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.DiscountEntity;
import com.kawashreh.ecommerce.order_service.domain.model.Discount;

import java.util.List;

public final class DiscountMapper {

    public static DiscountEntity toEntity(Discount d) {
        if (d == null) return null;
        return DiscountEntity.builder()
                .id(d.getId())
                .name(d.getName())
                .code(d.getCode())
                .description(d.getDescription())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .createdBy(d.getCreatedBy())
                .updatedBy(d.getUpdatedBy())
                .build();
    }

    public static Discount toDomain(DiscountEntity e) {
        if (e == null) return null;
        return Discount.builder()
                .id(e.getId())
                .name(e.getName())
                .code(e.getCode())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .createdBy(e.getCreatedBy())
                .updatedBy(e.getUpdatedBy())
                .build();
    }

    public static List<Discount> toDomainList(List<DiscountEntity> list) {
        if (list == null) return null;
        return list.stream()
                .map(DiscountMapper::toDomain)
                .toList();
    }

    public static List<DiscountEntity> toEntityList(List<Discount> list) {
        if (list == null) return null;
        return list.stream()
                .map(DiscountMapper::toEntity)
                .toList();
    }
}
