package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.constants.CacheConstants;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;

    public ProductServiceImpl(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Product> getAll() {
        List<ProductEntity> entities = repository.findAll();
        return ProductMapper.toDomainList(entities);
    }

    // unless = "#result == null": CacheConfig disables caching null values
    // (disableCachingNullValues()), and without this, @Cacheable's own attempt to store
    // a null result for a not-found id throws "Cache does not allow 'null' values"
    // instead of returning a clean 404 - a not-found lookup on any id crashed instead of
    // 404ing. Found live via a smoke test hitting GET /api/v1/product/{id} for an id
    // that doesn't exist.
    @Cacheable(value = CacheConstants.product_by_id, key = "#id", unless = "#result == null")
    @Override
    public Product find(UUID id) {
        return repository.findById(id).map(ProductMapper::toDomain).orElse(null);
    }

    @CacheEvict(value = CacheConstants.product_by_id, allEntries = true)
    @Override
    public Product save(Product product) {
        ProductEntity saved = repository.save(ProductMapper.toEntity(product));
        return ProductMapper.toDomain(saved);
    }

    @CacheEvict(value = CacheConstants.product_by_id, allEntries = true)
    @Override
    public void update(Product product) {
        repository.save(ProductMapper.toEntity(product));
    }

    @CacheEvict(value = CacheConstants.product_by_id, allEntries = true)
    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
