package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/v1/payment/process")
    PaymentDto processPayment(@RequestBody PaymentDto paymentDto);

    @GetMapping("/api/v1/payment/{paymentId}")
    PaymentDto getPayment(@PathVariable UUID paymentId);

    @GetMapping("/api/v1/payment/order/{orderId}")
    PaymentDto getPaymentByOrderId(@PathVariable UUID orderId);

    @PostMapping("/api/v1/payment/{paymentId}/refund")
    Boolean refundPayment(@PathVariable UUID paymentId);
}
