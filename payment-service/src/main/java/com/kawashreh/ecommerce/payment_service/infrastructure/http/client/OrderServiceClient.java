package com.kawashreh.ecommerce.payment_service.infrastructure.http.client;

import com.kawashreh.ecommerce.payment_service.constants.ApiPaths;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping(ApiPaths.ORDER_BASE + ApiPaths.ORDER_BY_ID)
    OrderDto retrieveOrder(@PathVariable UUID id);
}
