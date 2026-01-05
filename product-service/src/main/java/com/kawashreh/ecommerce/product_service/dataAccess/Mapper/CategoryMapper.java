package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.CategoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Category;

import java.util.List;

public final class CategoryMapper {

    public static CategoryEntity toEntity(Category d) {
        if (d == null) return null;
        CategoryEntity e = new CategoryEntity();
        e.setId(d.getId());
        e.setName(d.getName());
        e.setDescription(d.getDescription());
        return e;
    }

    public static Category toDomain(CategoryEntity e) {
        if (e == null) return null;
        Category d = new Category();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setDescription(e.getDescription());
        return d;
    }

    public static List<Category> toDomainList(List<CategoryEntity> list) {
        return list != null ? list.stream().map(CategoryMapper::toDomain).toList() : List.of();
    }

    public static List<CategoryEntity> toEntityList(List<Category> list) {
        return list != null ? list.stream().map(CategoryMapper::toEntity).toList() : List.of();
    }
}
