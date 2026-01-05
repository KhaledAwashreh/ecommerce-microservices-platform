package com.kawashreh.ecommerce.product_service.dataAccess.mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.CategoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Category;

import java.util.List;

public final class CategoryMapper {

    public static CategoryEntity toEntity(Category d) {
        if (d == null)
            return null;

        return CategoryEntity.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .build();
    }

    public static Category toDomain(CategoryEntity e) {
        return Category.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .build();
    }

    public static List<Category> toDomainList(List<CategoryEntity> list) {
        return list != null ? list.stream().map(CategoryMapper::toDomain).toList() : List.of();
    }

    public static List<CategoryEntity> toEntityList(List<Category> list) {
        return list != null ? list.stream().map(CategoryMapper::toEntity).toList() : List.of();
    }
}
