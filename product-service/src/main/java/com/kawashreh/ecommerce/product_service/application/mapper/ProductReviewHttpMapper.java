package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDto;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;

public final class ProductReviewHttpMapper {

    private ProductReviewHttpMapper() {} // Prevent instantiation

    // Domain -> DTO
    public static ProductReviewDto toDto(ProductReview review) {
        if (review == null) return null;

        return ProductReviewDto.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .review(review.getReview())
                .stars(review.getStars())
                .createdAt(review.getProduct().getCreatedAt())
                .build();
    }

    // DTO -> Domain
    public static ProductReview toDomain(ProductReviewDto dto) {
        if (dto == null) return null;

        return ProductReview.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .review(dto.getReview())
                .stars(dto.getStars())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
