package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Product Service.
 * Uses API Gateway for service discovery.
 */
@FeignClient(name = "product-service-UI-client", url = "${api.gateway.base-url}")
public interface ProductServiceClient {

    @GetMapping("/api/v1/product")
    List<ProductDto> getAllProducts();

    @GetMapping("/api/v1/product/{productId}")
    ProductDto getProductById(@PathVariable("productId") UUID productId);

    @GetMapping("/api/v1/categories")
    List<CategoryDto> getCategories();
}