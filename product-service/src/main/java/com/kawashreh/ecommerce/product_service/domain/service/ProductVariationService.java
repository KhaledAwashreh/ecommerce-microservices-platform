package com.kawashreh.ecommerce.product_service.domain.service;

import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;

import java.util.List;
import java.util.UUID;

public interface ProductVariationService {

    public List<ProductVariation> getAll();

    public ProductVariation find(UUID id);

    public void save(ProductVariation productVariation);

    public void update(ProductVariation productVariation);

    public void delete(UUID id);

    public List<ProductVariation> findByProductId(UUID productId);
}
