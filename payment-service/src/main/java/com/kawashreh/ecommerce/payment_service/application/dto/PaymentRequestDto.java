package com.kawashreh.ecommerce.payment_service.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequestDto {

    @NotNull
    private UUID orderId;

    @NotNull
    private UUID buyerId;

    // Not validated beyond presence in the DTO shape: PaymentServiceImpl re-derives the
    // authoritative amount from order-service rather than trusting this field (see
    // PaymentController), so a malformed/negative value here can't reach the DB anyway.
    private BigDecimal amount;

    @NotBlank
    private String paymentMethod;
}
