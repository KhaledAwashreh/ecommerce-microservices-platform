package com.kawashreh.ecommerce.payment_service.application.controller;

import com.kawashreh.ecommerce.payment_service.application.dto.PaymentRequestDto;
import com.kawashreh.ecommerce.payment_service.application.dto.PaymentResponseDto;
import com.kawashreh.ecommerce.payment_service.application.mapper.PaymentHttpMapper;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentRequestDto request) {
        Payment payment = paymentService.processPayment(
                request.getOrderId(),
                request.getBuyerId(),
                request.getPaymentMethod()
        );
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPayment(@PathVariable UUID paymentId) {
        Payment payment = paymentService.getPaymentById(paymentId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponseDto> getPaymentByOrderId(@PathVariable UUID orderId) {
        Payment payment = paymentService.getPaymentByOrderId(orderId);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentHttpMapper.toDto(payment));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Boolean> refundPayment(@PathVariable UUID paymentId) {
        boolean success = paymentService.refundPayment(paymentId);
        return ResponseEntity.ok(success);
    }
}
