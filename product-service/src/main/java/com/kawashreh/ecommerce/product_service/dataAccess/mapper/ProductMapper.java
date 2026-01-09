package com.kawashreh.ecommerce.product_service.dataAccess.mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Product;

import java.util.List;

public final class ProductMapper {

    public static ProductEntity toEntity(Product d) {
        if (d == null) return null;
        return ProductEntity.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .categories(CategoryMapper.toEntityList(d.getCategories()))
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .thumbnailUrl(d.getThumbnailUrl())
                .ownerId(d.getOwnerId())
                .build();
    }

    public static Product toDomain(ProductEntity e) {
        if (e == null) return null;
        return Product.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .categories(CategoryMapper.toDomainList(e.getCategories()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .ownerId(e.getOwnerId())
                .thumbnailUrl(e.getThumbnailUrl())
                .build();
    }

    public static List<Product> toDomainList(List<ProductEntity> list) {
        return list != null ? list.stream().map(ProductMapper::toDomain).toList() : List.of();
    }

    public static List<ProductEntity> toEntityList(List<Product> list) {
        return list != null ? list.stream().map(ProductMapper::toEntity).toList() : List.of();
    }
}
