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

    @Cacheable(value = CacheConstants.product_by_id, key = "#id" )
    @Override
    public Product find(UUID id) {
        return repository.findById(id).map(ProductMapper::toDomain).orElse(null);
    }

    @Override
    public void save(Product product) {
        repository.save(ProductMapper.toEntity(product));
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
