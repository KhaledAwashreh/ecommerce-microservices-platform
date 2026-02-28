package com.kawashreh.ecommerce.payment_service.domain.service.impl;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity;
import com.kawashreh.ecommerce.payment_service.dataAccess.mapper.PaymentMapper;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.dataAccess.mapper.PaymentMapper;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public Payment processPayment(UUID orderId, UUID buyerId, String paymentMethod) {
        logger.info("Processing payment for order: {}, buyer: {}, method: {}", orderId, buyerId, paymentMethod);

        // TODO: Integrate with actual payment gateway (Stripe, etc.)
        // For now, simulate successful payment

        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(BigDecimal.ZERO) // Would be fetched from order
                .paymentMethod(paymentMethod)
                .status(Payment.PaymentStatus.COMPLETED)
                .transactionId(UUID.randomUUID().toString())
                .paymentGateway("SIMULATED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var entity = PaymentMapper.toEntity(payment);
        var saved = paymentRepository.save(entity);
        
        logger.info("Payment processed successfully: {} for order: {}", saved.getId(), orderId);
        return PaymentMapper.toDomain(saved);
    }

    @Override
    public Payment getPaymentById(UUID paymentId) {
        logger.debug("Fetching payment by id: {}", paymentId);
        return paymentRepository.findById(paymentId)
                .map(PaymentMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Payment getPaymentByOrderId(UUID orderId) {
        logger.debug("Fetching payment by order id: {}", orderId);
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentMapper::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional
    public boolean refundPayment(UUID paymentId) {
        logger.info("Processing refund for payment: {}", paymentId);
        
        return paymentRepository.findById(paymentId)
                .map(entity -> {
                    entity.setStatus(PaymentEntity.PaymentStatus.REFUNDED);
                    paymentRepository.save(entity);
                    logger.info("Payment {} refunded successfully", paymentId);
                    return true;
                })
                .orElse(false);
    }
}
