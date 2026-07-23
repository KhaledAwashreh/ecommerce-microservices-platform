package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.ProductVariationDto;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;

public final class ProductVariationHttpMapper {

    private ProductVariationHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static ProductVariationDto toDto(ProductVariation variation) {
        if (variation == null) return null;

        return ProductVariationDto.builder()
                .id(variation.getId())
                .productId(variation.getProduct() != null ? variation.getProduct().getId() : null)
                .sku(variation.getSku())
                .name(variation.getName())
                .price(variation.getPrice())
                .stockQuantity(variation.getStockQuantity())
                .isActive(variation.getIsActive())
                .thumbnailUrl(variation.getThumbnailUrl())
                .createdAt(variation.getCreatedAt())
                .updatedAt(variation.getUpdatedAt())
                .build();
    }

    // DTO -> Domain
    public static ProductVariation toDomain(ProductVariationDto dto) {
        if (dto == null) return null;

        return ProductVariation.builder()
                .id(dto.getId())
                .sku(dto.getSku())
                .name(dto.getName())
                .price(dto.getPrice())
                .stockQuantity(dto.getStockQuantity())
                .isActive(dto.getIsActive())
                .thumbnailUrl(dto.getThumbnailUrl())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
