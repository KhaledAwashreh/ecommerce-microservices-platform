package com.kawashreh.ecommerce.payment_service.domain.service;

import com.kawashreh.ecommerce.payment_service.domain.model.Payment;

import java.util.UUID;

public interface PaymentService {

    Payment processPayment(UUID orderId, UUID buyerId, String paymentMethod);

    Payment getPaymentById(UUID paymentId);

    Payment getPaymentByOrderId(UUID orderId);

    boolean refundPayment(UUID paymentId);
}
