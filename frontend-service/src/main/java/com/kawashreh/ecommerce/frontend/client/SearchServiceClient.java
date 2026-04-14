package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.SearchResultDto;
import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Search Service.
 * Uses API Gateway for search functionality.
 */
@FeignClient(name = "search-service-UI-client", url = "${api.gateway.base-url}/api/v1/search")
public interface SearchServiceClient {

    @GetMapping("/products")
    List<SearchResultDto> searchProducts(@RequestParam("q") String query);

    @GetMapping("/products/autocomplete")
    List<String> autocomplete(@RequestParam("q") String query);

    @GetMapping("/trending")
    List<ProductDto> getTrendingProducts();

    @GetMapping("/recent")
    List<ProductDto> getRecentlyViewedProducts(@RequestParam("userId") UUID userId);
}