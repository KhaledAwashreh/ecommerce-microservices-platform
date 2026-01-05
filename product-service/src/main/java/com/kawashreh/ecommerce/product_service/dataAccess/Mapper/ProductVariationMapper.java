package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;

import java.util.List;

public final class ProductVariationMapper {

    public static ProductVariationEntity toEntity(ProductVariation d) {
        if (d == null) return null;
        ProductVariationEntity e = new ProductVariationEntity();
        e.setId(d.getId());
        e.setSku(d.getSku());
        e.setStockQuantity(d.getStockQuantity());
        e.setName(d.getName());
        e.setPrice(d.getPrice());
        e.setIsActive(d.getIsActive());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setThumbnailUrl(d.getThumbnailUrl());
        e.setAttachments(d.getAttachments());
        e.setAttributes(AttributeMapper.toEntityList(d.getAttributes()));
        e.setProduct(ProductMapper.toEntity(d.getProduct()));
        return e;
    }

    public static ProductVariation toDomain(ProductVariationEntity e) {
        if (e == null) return null;
        ProductVariation d = new ProductVariation();
        d.setId(e.getId());
        d.setSku(e.getSku());
        d.setStockQuantity(e.getStockQuantity());
        d.setName(e.getName());
        d.setPrice(e.getPrice());
        d.setIsActive(e.getIsActive());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        d.setThumbnailUrl(e.getThumbnailUrl());
        d.setAttachments(e.getAttachments());
        d.setAttributes(AttributeMapper.toDomainList(e.getAttributes()));
        d.setProduct(ProductMapper.toDomain(e.getProduct()));
        return d;
    }

    public static List<ProductVariation> toDomainList(List<ProductVariationEntity> list) {
        return list != null ? list.stream().map(ProductVariationMapper::toDomain).toList() : List.of();
    }

    public static List<ProductVariationEntity> toEntityList(List<ProductVariation> list) {
        return list != null ? list.stream().map(ProductVariationMapper::toEntity).toList() : List.of();
    }
}
