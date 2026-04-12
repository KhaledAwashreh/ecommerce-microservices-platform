package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.DiscountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Discounts.
 * Uses API Gateway - discounts managed by order-service.
 */
@FeignClient(name = "discount-service-UI-client", url = "${api.gateway.base-url}/api/v1/discounts")
public interface DiscountServiceClient {

    @GetMapping
    List<DiscountDto> getAllDiscounts();

    @GetMapping("/{discountId}")
    DiscountDto getDiscountById(@PathVariable("discountId") UUID discountId);

    @GetMapping("/code/{code}")
    DiscountDto getDiscountByCode(@PathVariable String code);

    @PostMapping("/validate")
    DiscountDto validateDiscount(@RequestBody DiscountDto discount);
}