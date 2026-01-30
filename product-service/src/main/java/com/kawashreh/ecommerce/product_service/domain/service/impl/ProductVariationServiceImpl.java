package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.Const.CacheConstants;
import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductVariationMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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


    @Caching(evict = {
            @CacheEvict(value = CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID, key = "#result.productId")
    })
    @Override
    public void update(ProductVariation productVariation) {
        repository.save(ProductVariationMapper.toEntity(productVariation));
    }


    @Caching(evict = {
            @CacheEvict(value = CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID, key = "#result.productId")
    })
    @Override
    public void delete(UUID id) {
        ProductVariationEntity entity = repository.findById(id).orElse(null);
        if (entity == null) return;

        repository.deleteById(id);
    }


    @Cacheable(value = CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID, key = "#productId" )
    @Override
    public List<ProductVariation> findByProductId(UUID productId) {
        List<ProductVariationEntity> entities = repository.findByProductId(productId);
        return ProductVariationMapper.toDomainList(entities);
    }
}
