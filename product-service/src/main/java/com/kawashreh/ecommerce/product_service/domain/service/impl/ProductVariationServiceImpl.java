package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductVariationMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
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
        List<ProductVariationEntity> entities = repository.findAll();
        return ProductVariationMapper.toDomainList(entities);
    }

    @Override
    public ProductVariation find(UUID id) {
        return repository.findById(id).map(ProductVariationMapper::toDomain).orElse(null);
    }

    @Override
    public void save(ProductVariation productVariation) {
        repository.save(ProductVariationMapper.toEntity(productVariation));
    }

    @Override
    public void update(ProductVariation productVariation) {
        repository.save(ProductVariationMapper.toEntity(productVariation));
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public List<ProductVariation> findByProductId(UUID productId) {
        List<ProductVariationEntity> entities = repository.findByProductId(productId);
        return ProductVariationMapper.toDomainList(entities);
    }
}
