package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductVariationServiceImpl implements ProductVariationService {

    private final ProductVariationRepository repository;

    public ProductVariationServiceImpl(ProductVariationRepository repository) {
        this.repository = repository;
    }
    @Override
    public List<ProductVariation> getAll() {
            return repository.findAll();
    }

    @Override
    public ProductVariation find(UUID id) {
        return repository.findById(id)
                .orElse(null);
    }

    @Override
    public void save(ProductVariation productVariation) {
        repository.save(productVariation);
    }

    @Override
    public void update(ProductVariation productVariation) {
        repository.save(productVariation);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public List<ProductVariation> findByProductId(UUID productId) {
        return repository.findByProductId(productId);
    }
}
