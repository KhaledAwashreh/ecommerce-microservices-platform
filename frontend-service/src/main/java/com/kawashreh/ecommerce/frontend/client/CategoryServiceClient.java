package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * Feign client for Category Service.
 * Uses Kubernetes DNS for service discovery: http://product-service:8080
 * Note: Category endpoints are in the product-service.
 */
@FeignClient(name = "category-service-UI-Client", url = "${api.gateway.base-url}/api/v1/categories")
public interface CategoryServiceClient {

    @GetMapping
    List<CategoryDto> getAllCategories();
}
