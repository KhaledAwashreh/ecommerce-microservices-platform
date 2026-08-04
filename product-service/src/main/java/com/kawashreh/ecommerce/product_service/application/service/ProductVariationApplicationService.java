package com.kawashreh.ecommerce.product_service.application.service;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
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
            // Matches GH #41's fix for ProductApplicationService/ReviewApplicationService,
            // which this method was missed by: silently returning null let the controller
            // respond 201 Created with an empty body for a nonexistent product.
            throw new NoSuchElementException("Product not found: " + productId);
        }

        variation.setProduct(product);
        return productVariationService.save(variation);
    }
}
