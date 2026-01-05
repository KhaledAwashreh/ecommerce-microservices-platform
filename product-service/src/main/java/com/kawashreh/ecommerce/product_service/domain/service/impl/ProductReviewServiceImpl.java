package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductReviewRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductReviewMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
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
        List<ProductReviewEntity> entities = repository.findAll();
        return ProductReviewMapper.toDomainList(entities);
    }

    @Override
    public ProductReview find(UUID id) {
        return repository.findById(id).map(ProductReviewMapper::toDomain).orElse(null);
    }

    @Override
    public void save(ProductReview productReview) {
        repository.save(ProductReviewMapper.toEntity(productReview));
    }

    @Override
    public void update(ProductReview productReview) {
        repository.save(ProductReviewMapper.toEntity(productReview));
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public List<ProductReview> findByUserId(UUID userId) {
        List<ProductReviewEntity> entities = repository.findByUserId(userId);
        return ProductReviewMapper.toDomainList(entities);
    }

    @Override
    public List<ProductReview> findByProductId(UUID productId) {
        List<ProductReviewEntity> entities = repository.findByProductId(productId);
        return ProductReviewMapper.toDomainList(entities);
    }
}
