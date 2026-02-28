package com.kawashreh.ecommerce.payment_service.application.mapper;

import com.kawashreh.ecommerce.payment_service.application.dto.PaymentResponseDto;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;

public final class PaymentHttpMapper {

    private PaymentHttpMapper() {}

    public static PaymentResponseDto toDto(Payment payment) {
        if (payment == null) return null;

        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .buyerId(payment.getBuyerId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .paymentGateway(payment.getPaymentGateway())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
