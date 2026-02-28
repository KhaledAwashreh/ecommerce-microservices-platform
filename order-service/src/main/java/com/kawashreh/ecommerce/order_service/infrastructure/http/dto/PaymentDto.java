package com.kawashreh.ecommerce.order_service.infrastructure.http.dto;

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
public class PaymentDto {

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
