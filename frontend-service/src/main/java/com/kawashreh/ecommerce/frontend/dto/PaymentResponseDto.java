package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {
    private UUID id;
    private UUID orderId;
    private UUID buyerId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String transactionId;
    private String paymentGateway;
    private Instant createdAt;
    private Instant updatedAt;
}
