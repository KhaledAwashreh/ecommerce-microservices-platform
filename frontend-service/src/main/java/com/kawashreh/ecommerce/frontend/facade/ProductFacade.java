package com.kawashreh.ecommerce.frontend.facade;

import com.kawashreh.ecommerce.frontend.client.CategoryServiceClient;
import com.kawashreh.ecommerce.frontend.client.ProductServiceClient;
import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import com.kawashreh.ecommerce.frontend.dto.facade.ProductWithDetailsDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductFacade {

    private final ProductServiceClient productServiceClient;
    private final CategoryServiceClient categoryServiceClient;

    public ProductFacade(ProductServiceClient productServiceClient,
                         CategoryServiceClient categoryServiceClient) {
        this.productServiceClient = productServiceClient;
        this.categoryServiceClient = categoryServiceClient;
    }

    public List<ProductDto> getAllProducts() {
        try {
            return productServiceClient.getAllProducts();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public ProductWithDetailsDto getProductWithDetails(UUID productId) {
        ProductDto product = null;
        CategoryDto category = null;

        try {
            product = productServiceClient.getProductById(productId);
        } catch (Exception e) {
            // product stays null
        }

        if (product != null && product.getCategories() != null && !product.getCategories().isEmpty()) {
            try {
                category = product.getCategories().get(0);
            } catch (Exception e) {
                // category stays null
            }
        }

        return ProductWithDetailsDto.builder()
                .product(product)
                .category(category)
                .build();
    }

    public List<ProductDto> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return getAllProducts();
        }
        return getAllProducts().stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<CategoryDto> getAllCategories() {
        try {
            return categoryServiceClient.getAllCategories();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}