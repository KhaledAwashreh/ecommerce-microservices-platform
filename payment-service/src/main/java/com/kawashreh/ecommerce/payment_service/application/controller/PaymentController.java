package com.kawashreh.ecommerce.payment_service.application.controller;

import com.kawashreh.ecommerce.payment_service.application.dto.PaymentRequestDto;
import com.kawashreh.ecommerce.payment_service.application.dto.PaymentResponseDto;
import com.kawashreh.ecommerce.payment_service.application.mapper.PaymentHttpMapper;
import com.kawashreh.ecommerce.payment_service.domain.exception.InvalidPaymentStateException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.payment_service.constants.ApiPaths;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.BASE_PATH)
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(ApiPaths.PROCESS)
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody @Valid PaymentRequestDto request) {
        // request.getAmount() is intentionally not passed through: PaymentServiceImpl
        // re-derives the authoritative amount from order-service so a client cannot
        // dictate what it pays.
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
        try {
            boolean success = paymentService.refundPayment(paymentId);
            if (!success) {
                // refundPayment() returns false for exactly one reason - no payment with
                // this id exists (every other outcome either returns true or throws
                // InvalidPaymentStateException below). Returning 200 OK here, as this did
                // previously, told a client its refund request was handled when nothing was
                // found to refund. Found live via a smoke test: refunding a random UUID
                // returned 200. Note the wrong-state case already correctly returns 409, so
                // the 200 was an oversight rather than a deliberate contract.
                logger.warn("Refund requested for unknown payment {}", paymentId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(false);
            }
            return ResponseEntity.ok(true);
        } catch (InvalidPaymentStateException e) {
            // Payment exists but isn't COMPLETED (or is already REFUNDED): a genuine, entirely
            // deterministic client-side conflict, not a server error. Mapped locally here
            // rather than via a module-wide GlobalExceptionHandler - this module has none
            // (root CLAUDE.md) and a single call site doesn't warrant adding one.
            logger.warn("Refund rejected for payment {}: {}", paymentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(false);
        }
    }
}
