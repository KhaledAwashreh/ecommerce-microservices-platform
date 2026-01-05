package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.AttributeEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Attribute;

import java.util.List;

public final class AttributeMapper {

    public static AttributeEntity toEntity(Attribute d) {
        if (d == null) return null;
        AttributeEntity e = new AttributeEntity();
        e.setId(d.getId());
        e.setName(d.getName());
        e.setValue(d.getValue());
        return e;
    }

    public static Attribute toDomain(AttributeEntity e) {
        if (e == null) return null;
        Attribute d = new Attribute();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setValue(e.getValue());
        return d;
    }

    public static List<Attribute> toDomainList(List<AttributeEntity> list) {
        return list != null ? list.stream().map(AttributeMapper::toDomain).toList() : List.of();
    }

    public static List<AttributeEntity> toEntityList(List<Attribute> list) {
        return list != null ? list.stream().map(AttributeMapper::toEntity).toList() : List.of();
    }
}
