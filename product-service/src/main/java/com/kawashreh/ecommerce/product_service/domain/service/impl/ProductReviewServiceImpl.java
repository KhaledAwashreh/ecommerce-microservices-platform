package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductReviewRepository;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository repository;

    public ProductReviewServiceImpl(ProductReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ProductReview> getAll() {
        return repository.findAll();
    }

    @Override
    public ProductReview find(UUID id) {
        return repository.findById(id)
                .orElse(null);
    }

    @Override
    public void save(ProductReview productReview) {

        repository.save(productReview);
    }

    @Override
    public void update(ProductReview productReview) {

        repository.save(productReview);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public List<ProductReview> findByUserId(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public ProductReview findByProductId(UUID productId) {
        return null;
    }
}
