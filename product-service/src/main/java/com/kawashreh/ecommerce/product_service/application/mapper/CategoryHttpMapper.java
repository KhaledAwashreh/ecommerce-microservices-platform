package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.CategoryDto;
import com.kawashreh.ecommerce.product_service.domain.model.Category;

public final class CategoryHttpMapper {

    private CategoryHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static CategoryDto toDto(Category category) {
        if (category == null) return null;

        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }

    // DTO -> Domain
    public static Category toDomain(CategoryDto dto) {
        if (dto == null) return null;

        return Category.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }
}
