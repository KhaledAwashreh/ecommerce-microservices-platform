package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Category Service.
 * Uses API Gateway - categories are managed by product-service.
 */
@FeignClient(name = "category-service-UI-Client", url = "${api.gateway.base-url}/api/v1/categories")
public interface CategoryServiceClient {

    @GetMapping
    List<CategoryDto> getAllCategories();

    @GetMapping("/{categoryId}")
    CategoryDto getCategoryById(@PathVariable("categoryId") UUID categoryId);

    @GetMapping("/name/{categoryName}")
    CategoryDto getCategoryByName(@PathVariable String categoryName);
}
