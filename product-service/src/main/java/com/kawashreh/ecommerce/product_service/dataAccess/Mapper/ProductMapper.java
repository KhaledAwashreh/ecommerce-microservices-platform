package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Product;

import java.util.List;

public final class ProductMapper {

    public static ProductEntity toEntity(Product d) {
        if (d == null) return null;
        ProductEntity e = new ProductEntity();
        e.setId(d.getId());
        e.setName(d.getName());
        e.setDescription(d.getDescription());
        e.setCategories(CategoryMapper.toEntityList(d.getCategories()));
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setThumbnailUrl(d.getThumbnailUrl());
        return e;
    }

    public static Product toDomain(ProductEntity e) {
        if (e == null) return null;
        Product d = new Product();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setDescription(e.getDescription());
        d.setCategories(CategoryMapper.toDomainList(e.getCategories()));
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        d.setThumbnailUrl(e.getThumbnailUrl());
        return d;
    }

    public static List<Product> toDomainList(List<ProductEntity> list) {
        return list != null ? list.stream().map(ProductMapper::toDomain).toList() : List.of();
    }

    public static List<ProductEntity> toEntityList(List<Product> list) {
        return list != null ? list.stream().map(ProductMapper::toEntity).toList() : List.of();
    }
}
