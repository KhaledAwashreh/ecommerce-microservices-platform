package com.kawashreh.ecommerce.product_service.domain.service;

import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;

import java.util.List;
import java.util.UUID;

public interface ProductReviewService {

    public List<ProductReview> getAll();

    public ProductReview find(UUID id);

    public ProductReview save(ProductReview productReview);

    public ProductReview update(ProductReview productReview);

    public void delete(UUID id);

    public void findByName(String name);

    public ProductVariation findByProductId(UUID productId);

}
