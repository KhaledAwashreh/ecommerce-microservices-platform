package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.PaymentRequestDto;
import com.kawashreh.ecommerce.frontend.dto.PaymentResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Feign client for Payment Service.
 * Uses Kubernetes DNS for service discovery: http://payment-service:8080
 */
@FeignClient(name = "payment-service-UI-client", url = "http://payment-service:8080")
public interface PaymentServiceClient {

    @PostMapping("/api/v1/payment/process")
    PaymentResponseDto processPayment(@RequestBody PaymentRequestDto request);

    @GetMapping("/api/v1/payment/{paymentId}")
    PaymentResponseDto getPayment(@PathVariable UUID paymentId);

    @GetMapping("/api/v1/payment/order/{orderId}")
    PaymentResponseDto getPaymentByOrderId(@PathVariable UUID orderId);
}
