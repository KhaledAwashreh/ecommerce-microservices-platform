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
 * Routes through API Gateway for auth, circuit-breaking, and retry.
 */
@FeignClient(name = "payment-service-UI-client", url = "${api.gateway.base-url}/api/v1/payment")
public interface PaymentServiceClient {

    @PostMapping("/process")
    PaymentResponseDto processPayment(@RequestBody PaymentRequestDto request);

    @GetMapping("/{paymentId}")
    PaymentResponseDto getPayment(@PathVariable("paymentId") UUID paymentId);

    @GetMapping("/order/{orderId}")
    PaymentResponseDto getPaymentByOrderId(@PathVariable("orderId") UUID orderId);
}
