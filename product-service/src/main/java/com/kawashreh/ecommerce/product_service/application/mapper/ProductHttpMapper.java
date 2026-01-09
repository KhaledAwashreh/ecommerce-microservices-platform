package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.ProductDto;
import com.kawashreh.ecommerce.product_service.domain.model.Product;

public final class ProductHttpMapper {

    private ProductHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static ProductDto toDto(Product product) {
        if (product == null) return null;

        return ProductDto.builder()
                .id(product.getId())
                .ownerId(product.getOwnerId())
                .name(product.getName())
                .description(product.getDescription())
                .categories(product.getCategories() != null ? 
                    product.getCategories().stream()
                        .map(CategoryHttpMapper::toDto)
                        .toList() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .thumbnailUrl(product.getThumbnailUrl())
                .build();
    }

    // DTO -> Domain
    public static Product toDomain(ProductDto dto) {
        if (dto == null) return null;

        return Product.builder()
                .id(dto.getId())
                .ownerId(dto.getOwnerId())
                .name(dto.getName())
                .description(dto.getDescription())
                .categories(dto.getCategories() != null ? 
                    dto.getCategories().stream()
                        .map(CategoryHttpMapper::toDomain)
                        .toList() : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .thumbnailUrl(dto.getThumbnailUrl())
                .build();
    }
}
