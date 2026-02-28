package com.kawashreh.ecommerce.payment_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

    private UUID id;

    private UUID orderId;

    private UUID buyerId;

    private BigDecimal amount;

    private String paymentMethod;

    private PaymentStatus status;

    private String transactionId;

    private String paymentGateway;

    private Instant createdAt;

    private Instant updatedAt;

    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REFUNDED,
        CANCELLED
    }
}
