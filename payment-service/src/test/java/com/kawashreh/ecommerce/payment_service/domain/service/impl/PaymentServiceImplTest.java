package com.kawashreh.ecommerce.payment_service.domain.service.impl;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity;
import com.kawashreh.ecommerce.payment_service.domain.exception.InvalidPaymentStateException;
import com.kawashreh.ecommerce.payment_service.domain.exception.OrderServiceException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.client.OrderServiceClient;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderDto;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit tests for PaymentServiceImpl.processPayment - covering issue #10
 * (amount was always persisted as ZERO because it was never derived from the order) and
 * issue #11 (no idempotency check / no unique constraint on order_id, so retries created
 * duplicate payment rows). These run without Docker/Spring context - no Testcontainers
 * involved.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    private PaymentServiceImpl paymentService;

    private UUID orderId;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(paymentRepository, orderServiceClient);
        orderId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
    }

    @Test
    void processPayment_derivesAmountFromOrderItems_notFromCaller() {
        OrderItemDto item1 = OrderItemDto.builder()
                .id(UUID.randomUUID())
                .productSku(UUID.randomUUID())
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .build();
        OrderItemDto item2 = OrderItemDto.builder()
                .id(UUID.randomUUID())
                .productSku(UUID.randomUUID())
                .quantity(3)
                .unitPrice(new BigDecimal("5.50"))
                .build();
        OrderDto order = OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(List.of(item1, item2))
                .build();

        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(order);
        when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.processPayment(orderId, buyerId, "CREDIT_CARD");

        // 2 * 10.00 + 3 * 5.50 = 36.50
        assertThat(result.getAmount()).isEqualByComparingTo("36.50");

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    void processPayment_throws_whenOrderNotFound_andNeverPersistsAZeroAmountPayment() {
        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(null);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class)
                .hasMessageContaining(orderId.toString());

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_throws_whenOrderServiceCallFails_andNeverPersistsAZeroAmountPayment() {
        when(orderServiceClient.retrieveOrder(orderId))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class);

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_throws_whenOrderHasNoItems() {
        OrderDto order = OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(List.of())
                .build();
        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(order);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class);

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_throws_whenOrderItemHasNoUnitPrice() {
        OrderItemDto item = OrderItemDto.builder()
                .id(UUID.randomUUID())
                .productSku(UUID.randomUUID())
                .quantity(1)
                .unitPrice(null)
                .build();
        OrderDto order = OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(List.of(item))
                .build();
        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(order);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class);

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_doesNotWrapAnAlreadyThrownOrderServiceException() {
        OrderServiceException original = new OrderServiceException("Order not found", 404);
        when(orderServiceClient.retrieveOrder(orderId)).thenThrow(original);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isSameAs(original);
    }

    // --- Issue #11: idempotency ---------------------------------------------------------

    @Test
    void processPayment_returnsExistingPayment_whenOnePaymentAlreadyExistsForOrder_withoutCallingOrderService() {
        PaymentEntity existingEntity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(new BigDecimal("36.50"))
                .paymentMethod("CREDIT_CARD")
                .status(PaymentEntity.PaymentStatus.COMPLETED)
                .paymentGateway("SIMULATED")
                .build();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingEntity));

        Payment result = paymentService.processPayment(orderId, buyerId, "CREDIT_CARD");

        assertThat(result.getId()).isEqualTo(existingEntity.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("36.50");
        verify(orderServiceClient, never()).retrieveOrder(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processPayment_returnsWinningPayment_whenConcurrentInsertViolatesUniqueConstraintOnOrderId() {
        OrderItemDto item = OrderItemDto.builder()
                .id(UUID.randomUUID())
                .productSku(UUID.randomUUID())
                .quantity(1)
                .unitPrice(new BigDecimal("20.00"))
                .build();
        OrderDto order = OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(List.of(item))
                .build();
        PaymentEntity winner = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(new BigDecimal("20.00"))
                .paymentMethod("CREDIT_CARD")
                .status(PaymentEntity.PaymentStatus.COMPLETED)
                .paymentGateway("SIMULATED")
                .build();

        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(order);
        // First check (fast path) finds nothing; a concurrent request wins the insert race;
        // the fallback lookup after the constraint violation then finds its row.
        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_payment_order_id\""));

        Payment result = paymentService.processPayment(orderId, buyerId, "CREDIT_CARD");

        assertThat(result.getId()).isEqualTo(winner.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("20.00");
        verify(paymentRepository, times(2)).findByOrderId(orderId);
    }

    @Test
    void processPayment_rethrowsConstraintViolation_whenNoExistingPaymentIsFoundAfterAll() {
        OrderItemDto item = OrderItemDto.builder()
                .id(UUID.randomUUID())
                .productSku(UUID.randomUUID())
                .quantity(1)
                .unitPrice(new BigDecimal("20.00"))
                .build();
        OrderDto order = OrderDto.builder()
                .id(orderId)
                .buyer(buyerId)
                .selectedItems(List.of(item))
                .build();
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_payment_order_id\"");

        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(order);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(PaymentEntity.class))).thenThrow(violation);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isSameAs(violation);
    }

    // --- Issue #12: refund state guard ---------------------------------------------------

    @Test
    void refundPayment_refundsACompletedPayment_andReturnsTrue() {
        UUID paymentId = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(new BigDecimal("20.00"))
                .paymentMethod("CREDIT_CARD")
                .status(PaymentEntity.PaymentStatus.COMPLETED)
                .paymentGateway("SIMULATED")
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(entity));

        boolean result = paymentService.refundPayment(paymentId);

        assertThat(result).isTrue();
        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentEntity.PaymentStatus.REFUNDED);
    }

    @Test
    void refundPayment_returnsFalse_whenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        boolean result = paymentService.refundPayment(paymentId);

        assertThat(result).isFalse();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_rejectsASecondRefund_onAnAlreadyRefundedPayment() {
        UUID paymentId = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(new BigDecimal("20.00"))
                .paymentMethod("CREDIT_CARD")
                .status(PaymentEntity.PaymentStatus.REFUNDED)
                .paymentGateway("SIMULATED")
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining(paymentId.toString());

        verify(paymentRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentEntity.PaymentStatus.class, names = {"COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void refundPayment_rejectsRefund_whenPaymentIsNotCompleted(PaymentEntity.PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        PaymentEntity entity = PaymentEntity.builder()
                .id(paymentId)
                .orderId(orderId)
                .buyerId(buyerId)
                .amount(new BigDecimal("20.00"))
                .paymentMethod("CREDIT_CARD")
                .status(status)
                .paymentGateway("SIMULATED")
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> paymentService.refundPayment(paymentId))
                .isInstanceOf(InvalidPaymentStateException.class);

        verify(paymentRepository, never()).save(any());
    }
}
