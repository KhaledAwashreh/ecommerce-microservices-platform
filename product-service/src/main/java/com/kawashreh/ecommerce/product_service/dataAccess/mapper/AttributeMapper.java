package com.kawashreh.ecommerce.product_service.dataAccess.mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.AttributeEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Attribute;

import java.util.List;

public final class AttributeMapper {

    public static AttributeEntity toEntity(Attribute d) {
        if (d == null) return null;
        return AttributeEntity.builder()
                .id(d.getId())
                .name(d.getName())
                .value(d.getValue())
                .build();
    }

    public static Attribute toDomain(AttributeEntity e) {
        if (e == null) return null;
        return Attribute.builder()
                .id(e.getId())
                .name(e.getName())
                .value(e.getValue())
                .build();
    }

    public static List<Attribute> toDomainList(List<AttributeEntity> list) {
        return list != null ? list.stream().map(AttributeMapper::toDomain).toList() : List.of();
    }

    public static List<AttributeEntity> toEntityList(List<Attribute> list) {
        return list != null ? list.stream().map(AttributeMapper::toEntity).toList() : List.of();
    }
}
