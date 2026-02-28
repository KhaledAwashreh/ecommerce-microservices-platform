package com.kawashreh.ecommerce.payment_service.dataAccess.mapper;

import com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;

public final class PaymentMapper {

    private PaymentMapper() {}

    // Entity -> Domain
    public static Payment toDomain(PaymentEntity entity) {
        if (entity == null) return null;

        return Payment.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .buyerId(entity.getBuyerId())
                .amount(entity.getAmount())
                .paymentMethod(entity.getPaymentMethod())
                .status(mapStatusToDomain(entity.getStatus()))
                .transactionId(entity.getTransactionId())
                .paymentGateway(entity.getPaymentGateway())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // Domain -> Entity
    public static PaymentEntity toEntity(Payment payment) {
        if (payment == null) return null;

        return PaymentEntity.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .buyerId(payment.getBuyerId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(mapStatusToEntity(payment.getStatus()))
                .transactionId(payment.getTransactionId())
                .paymentGateway(payment.getPaymentGateway())
                .build();
    }

    private static Payment.PaymentStatus mapStatusToDomain(PaymentEntity.PaymentStatus status) {
        if (status == null) return null;
        return Payment.PaymentStatus.valueOf(status.name());
    }

    private static PaymentEntity.PaymentStatus mapStatusToEntity(Payment.PaymentStatus status) {
        if (status == null) return null;
        return PaymentEntity.PaymentStatus.valueOf(status.name());
    }
}
