package com.kawashreh.ecommerce.product_service.dataAccess.Mapper;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;

import java.util.List;

public final class ProductReviewMapper {

    public static ProductReviewEntity toEntity(ProductReview d) {
        if (d == null) return null;
        ProductReviewEntity e = new ProductReviewEntity();
        e.setId(d.getId());
        e.setUserId(d.getUserId());
        e.setProduct(ProductMapper.toEntity(d.getProduct()));
        e.setReview(d.getReview());
        e.setStars(d.getStars());
        return e;
    }

    public static ProductReview toDomain(ProductReviewEntity e) {
        if (e == null) return null;
        ProductReview d = new ProductReview();
        d.setId(e.getId());
        d.setUserId(e.getUserId());
        d.setProduct(ProductMapper.toDomain(e.getProduct()));
        d.setReview(e.getReview());
        d.setStars(e.getStars());
        return d;
    }

    public static List<ProductReview> toDomainList(List<ProductReviewEntity> list) {
        return list != null ? list.stream().map(ProductReviewMapper::toDomain).toList() : List.of();
    }

    public static List<ProductReviewEntity> toEntityList(List<ProductReview> list) {
        return list != null ? list.stream().map(ProductReviewMapper::toEntity).toList() : List.of();
    }
}
