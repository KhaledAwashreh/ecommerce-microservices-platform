package com.kawashreh.ecommerce.payment_service;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.domain.exception.InvalidPaymentStateException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.domain.service.PaymentService;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.client.OrderServiceClient;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderDto;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Real-Postgres-via-Testcontainers coverage for payment-service (GH #45): the module
 * had a {@code BaseIntegrationTest} scaffold with no subclass, so nothing ever ran
 * against a real database - only PaymentServiceImplTest's plain-Mockito unit tests
 * (repository fully mocked) existed. This wires up the dead scaffold with the path the
 * issue calls out as worth real tests: payment processing, exercised end to end
 * through {@link PaymentService} against a real Postgres instance, including the
 * unique-constraint-backed idempotency behavior (GH #11) and the refund status-
 * transition guard (GH #12) that a mocked repository can't meaningfully verify.
 *
 * <p>OrderServiceClient (Feign, calls order-service) is the only mocked collaborator -
 * order-service isn't part of this test's container set.
 */
@ActiveProfiles("test")
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    private OrderDto orderWithAmount(UUID orderId, UUID buyerId, BigDecimal unitPrice, int quantity) {
        return OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(java.util.List.of(
                        OrderItemDto.builder()
                                .id(UUID.randomUUID())
                                .productSku(UUID.randomUUID())
                                .quantity(quantity)
                                .unitPrice(unitPrice)
                                .build()
                ))
                .build();
    }

    @Test
    void processPayment_persistsAndDerivesAmountFromOrderService() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderServiceClient.retrieveOrder(orderId))
                .thenReturn(orderWithAmount(orderId, buyerId, BigDecimal.valueOf(25), 3));

        Payment result = paymentService.processPayment(orderId, buyerId, "CARD");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(75));
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void processPayment_isIdempotent_returnsSameRowOnRetryForSameOrder() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderServiceClient.retrieveOrder(orderId))
                .thenReturn(orderWithAmount(orderId, buyerId, BigDecimal.TEN, 1));

        Payment first = paymentService.processPayment(orderId, buyerId, "CARD");
        Payment retry = paymentService.processPayment(orderId, buyerId, "CARD");

        // GH #11: a retry for the same order must return the SAME persisted row, not
        // create (or attempt to create) a second one - the DB's unique constraint on
        // order_id is what ultimately backs this, not just the pre-check.
        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void refundPayment_movesCompletedPaymentToRefunded_againstRealDb() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderServiceClient.retrieveOrder(orderId))
                .thenReturn(orderWithAmount(orderId, buyerId, BigDecimal.TEN, 1));
        Payment payment = paymentService.processPayment(orderId, buyerId, "CARD");

        boolean refunded = paymentService.refundPayment(payment.getId());

        assertThat(refunded).isTrue();
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity.PaymentStatus.REFUNDED);
    }

    @Test
    void refundPayment_rejectsSecondRefund_ofAnAlreadyRefundedPayment() {
        UUID orderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        when(orderServiceClient.retrieveOrder(orderId))
                .thenReturn(orderWithAmount(orderId, buyerId, BigDecimal.TEN, 1));
        Payment payment = paymentService.processPayment(orderId, buyerId, "CARD");
        paymentService.refundPayment(payment.getId());

        assertThatThrownBy(() -> paymentService.refundPayment(payment.getId()))
                .isInstanceOf(InvalidPaymentStateException.class);
    }
}
