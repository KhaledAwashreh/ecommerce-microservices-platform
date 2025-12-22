package com.kawashreh.ecommerce.product_service.domain.service;

import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;

import java.util.List;
import java.util.UUID;

public interface ProductReviewService {

    public List<ProductReview> getAll();

    public ProductReview find(UUID id);

    public void save(ProductReview productReview);

    public void update(ProductReview productReview);

    public void delete(UUID id);

    List<ProductReview> findByUserId(UUID userId);

    public ProductReview findByProductId(UUID productId);

}
