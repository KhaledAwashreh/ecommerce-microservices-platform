package com.kawashreh.ecommerce.payment_service.domain.service;

import com.kawashreh.ecommerce.payment_service.domain.exception.InvalidPaymentStateException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;

import java.util.UUID;

public interface PaymentService {

    Payment processPayment(UUID orderId, UUID buyerId, String paymentMethod);

    Payment getPaymentById(UUID paymentId);

    Payment getPaymentByOrderId(UUID orderId);

    /**
     * Refunds the given payment. Only a {@code COMPLETED} payment is eligible.
     *
     * @return {@code true} if the payment existed and was refunded; {@code false} if no
     *         payment exists for {@code paymentId}
     * @throws InvalidPaymentStateException if the payment exists but is not {@code
     *         COMPLETED} (including one that is already {@code REFUNDED}) - a second refund
     *         is rejected rather than reported as success
     */
    boolean refundPayment(UUID paymentId);
}
