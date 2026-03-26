package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Product Service.
 * Uses Kubernetes DNS for service discovery: http://product-service:8080
 */
@FeignClient(name = "product-service-UI-client", url = "${api.gateway.base-url}/api/v1/product")
public interface ProductServiceClient {

    @GetMapping
    List<ProductDto> getAllProducts();

    @GetMapping("/{productId}")
    ProductDto getProductById(@PathVariable UUID productId);
}