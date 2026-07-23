package com.kawashreh.ecommerce.product_service.application.service;

import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class ProductVariationApplicationService {

    private final ProductVariationService productVariationService;
    private final ProductService productService;

    public ProductVariationApplicationService(ProductVariationService productVariationService, ProductService productService) {
        this.productVariationService = productVariationService;
        this.productService = productService;
    }

    public ProductVariation createVariation(ProductVariation variation, UUID productId) {
        Product product = productService.find(productId);
        if (Objects.isNull(product)) {
            return null;
        }

        variation.setProduct(product);
        productVariationService.save(variation);
        return variation;
    }
}
