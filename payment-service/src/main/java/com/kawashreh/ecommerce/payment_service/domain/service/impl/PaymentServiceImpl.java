package com.kawashreh.ecommerce.payment_service.domain.service.impl;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity;
import com.kawashreh.ecommerce.payment_service.dataAccess.mapper.PaymentMapper;
import com.kawashreh.ecommerce.payment_service.domain.exception.OrderServiceException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.client.OrderServiceClient;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderDto;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OrderServiceClient orderServiceClient) {
        this.paymentRepository = paymentRepository;
        this.orderServiceClient = orderServiceClient;
    }

    @Override
    public Payment processPayment(UUID orderId, UUID buyerId, String paymentMethod) {
        logger.info("Processing payment for order: {}, buyer: {}, method: {}", orderId, buyerId, paymentMethod);

        // Idempotency fast path: a payment already exists for this order (client retry,
        // double-click, or a gateway-level retry replaying the same request). Return the
        // existing row as-is and skip the order-service round trip entirely - re-deriving
        // the amount would be pointless work and, if order-service is unavailable, would
        // turn a harmless retry into an avoidable OrderServiceException. Partial payments
        // per order are not an intended concept today (see PaymentEntity's unique
        // constraint on order_id), so "one payment per order" is the right read of a
        // repeat call, not an error.
        Payment existing = findExistingPayment(orderId);
        if (existing != null) {
            logger.info("Payment already exists for order {}: returning existing payment {}", orderId, existing.getId());
            return existing;
        }

        // The caller-supplied amount (PaymentRequestDto.amount) is intentionally never
        // read here. Trusting it would let a client dictate what it pays, so the
        // authoritative amount is always re-derived from order-service instead.
        BigDecimal amount = resolveOrderAmount(orderId);

        Payment payment = Payment.builder()
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status(Payment.PaymentStatus.COMPLETED)
                .paymentGateway("SIMULATED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        var entity = PaymentMapper.toEntity(payment);

        // No @Transactional spans the check-then-insert above: each repository call gets
        // its own transaction. That matters here - saveAndFlush forces the INSERT (and
        // therefore a unique-constraint violation on order_id) to happen synchronously on
        // this line, inside its own transaction, which then rolls back cleanly on failure.
        // If this were all one outer transaction instead, Postgres would mark it aborted
        // after the constraint violation, and the fallback findByOrderId lookup below would
        // itself fail instead of finding the winning row.
        PaymentEntity saved;
        try {
            saved = paymentRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Lost the race: another request for the same orderId committed first and the
            // unique constraint on order_id (PaymentEntity) is what actually stopped this
            // second insert - the findByOrderId check above only narrows the window, it
            // doesn't close it. Return the winner's payment instead of surfacing a 500.
            logger.warn("Concurrent payment insert detected for order {}; returning existing payment instead of failing", orderId);
            Payment winner = findExistingPayment(orderId);
            if (winner == null) {
                // Should not happen - the constraint only fires if a row exists - but don't
                // swallow the error if it somehow does.
                throw e;
            }
            return winner;
        }

        logger.info("Payment processed successfully: {} for order: {}", saved.getId(), orderId);
        return PaymentMapper.toDomain(saved);
    }

    private Payment findExistingPayment(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentMapper::toDomain)
                .orElse(null);
    }

    /**
     * Fetches the order from order-service and sums unitPrice * quantity across its
     * selected items to obtain the authoritative payment amount. Order-service's
     * Discount model currently carries no monetary value (name/code/description only),
     * so there is no discount amount to net out here - if that ever changes, this needs
     * to be revisited.
     * <p>
     * Fails loudly (throws {@link OrderServiceException}, an unchecked exception) on any
     * lookup failure - missing order, non-2xx response, circuit open, timeout, or any
     * other Feign error - rather than falling back to a zero/guessed amount. Since this
     * method runs before {@code paymentRepository.save}, no payment row is persisted
     * when it throws.
     */
    private BigDecimal resolveOrderAmount(UUID orderId) {
        OrderDto order;
        try {
            order = orderServiceClient.retrieveOrder(orderId);
        } catch (OrderServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to fetch order {} from order-service", orderId, e);
            throw new OrderServiceException(
                    "Unable to determine payment amount: order-service call failed for order " + orderId, e);
        }

        if (order == null) {
            throw new OrderServiceException("Order not found: " + orderId);
        }

        if (order.getSelectedItems() == null || order.getSelectedItems().isEmpty()) {
            throw new OrderServiceException(
                    "Order " + orderId + " has no items; cannot determine payment amount");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemDto item : order.getSelectedItems()) {
            if (item.getUnitPrice() == null) {
                throw new OrderServiceException(
                        "Order " + orderId + " item " + item.getId() + " has no unit price; cannot determine payment amount");
            }
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
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
