package com.kawashreh.ecommerce.payment_service.application.controller;

import com.kawashreh.ecommerce.payment_service.application.dto.PaymentRequestDto;
import com.kawashreh.ecommerce.payment_service.application.dto.PaymentResponseDto;
import com.kawashreh.ecommerce.payment_service.application.mapper.PaymentHttpMapper;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.payment_service.constants.ApiPaths;

import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(ApiPaths.PROCESS)
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentRequestDto request) {
        Payment payment = paymentService.processPayment(
                request.getOrderId(),
                request.getBuyerId(),
                request.getPaymentMethod()
        );
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @GetMapping(ApiPaths.PAYMENT_BY_ID)
    public ResponseEntity<PaymentResponseDto> getPayment(@PathVariable UUID paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @GetMapping(ApiPaths.PAYMENT_BY_ORDER)
    public ResponseEntity<PaymentResponseDto> getPaymentByOrderId(@PathVariable UUID orderId) {
        Payment payment = paymentService.getPaymentByOrderId(orderId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @PostMapping(ApiPaths.REFUND)
    public ResponseEntity<Boolean> refundPayment(@PathVariable UUID paymentId) {
        boolean success = paymentService.refundPayment(paymentId);
        return ResponseEntity.ok(success);
    }
}
