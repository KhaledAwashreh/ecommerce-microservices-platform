package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;

import java.util.List;

public final class ProductVariationMapper {

    public static ProductVariationEntity toEntity(ProductVariation d) {
        if (d == null) return null;
        return ProductVariationEntity.builder()
                .id(d.getId())
                .sku(d.getSku())
                .stockQuantity(d.getStockQuantity())
                .name(d.getName())
                .price(d.getPrice())
                .isActive(d.getIsActive())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .thumbnailUrl(d.getThumbnailUrl())
                .attachments(d.getAttachments())
                .attributes(AttributeMapper.toEntityList(d.getAttributes()))
                .product(ProductMapper.toEntity(d.getProduct()))
                .build();
    }

    public static ProductVariation toDomain(ProductVariationEntity e) {
        if (e == null) return null;
        return ProductVariation.builder()
                .id(e.getId())
                .sku(e.getSku())
                .stockQuantity(e.getStockQuantity())
                .name(e.getName())
                .price(e.getPrice())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .thumbnailUrl(e.getThumbnailUrl())
                .attachments(e.getAttachments())
                .attributes(AttributeMapper.toDomainList(e.getAttributes()))
                .product(ProductMapper.toDomain(e.getProduct()))
                .build();
    }

    public static List<ProductVariation> toDomainList(List<ProductVariationEntity> list) {
        return list != null ? list.stream().map(ProductVariationMapper::toDomain).toList() : List.of();
    }

    public static List<ProductVariationEntity> toEntityList(List<ProductVariation> list) {
        return list != null ? list.stream().map(ProductVariationMapper::toEntity).toList() : List.of();
    }
}
