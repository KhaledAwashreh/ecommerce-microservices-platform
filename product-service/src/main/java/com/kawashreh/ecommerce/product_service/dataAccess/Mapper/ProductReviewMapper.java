package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;

import java.util.List;

public final class ProductReviewMapper {

    public static ProductReviewEntity toEntity(ProductReview d) {
        if (d == null) return null;
        return ProductReviewEntity.builder()
                .id(d.getId())
                .userId(d.getUserId())
                .product(ProductMapper.toEntity(d.getProduct()))
                .review(d.getReview())
                .stars(d.getStars())
                .build();
    }

    public static ProductReview toDomain(ProductReviewEntity e) {
        if (e == null) return null;
        return ProductReview.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .product(ProductMapper.toDomain(e.getProduct()))
                .review(e.getReview())
                .stars(e.getStars())
                .build();
    }

    public static List<ProductReview> toDomainList(List<ProductReviewEntity> list) {
        return list != null ? list.stream().map(ProductReviewMapper::toDomain).toList() : List.of();
    }

    public static List<ProductReviewEntity> toEntityList(List<ProductReview> list) {
        return list != null ? list.stream().map(ProductReviewMapper::toEntity).toList() : List.of();
    }
}
