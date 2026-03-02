package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;

import java.util.UUID;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping(ApiPaths.PAYMENT_BASE + ApiPaths.PAYMENT_PROCESS)
    PaymentDto processPayment(@RequestBody PaymentDto paymentDto);

    @GetMapping(ApiPaths.PAYMENT_BASE + ApiPaths.PAYMENT_BY_ID)
    PaymentDto getPayment(@PathVariable UUID paymentId);

    @GetMapping(ApiPaths.PAYMENT_BASE + ApiPaths.PAYMENT_BY_ORDER)
    PaymentDto getPaymentByOrderId(@PathVariable UUID orderId);

    @PostMapping(ApiPaths.PAYMENT_BASE + ApiPaths.PAYMENT_REFUND)
    Boolean refundPayment(@PathVariable UUID paymentId);
}
