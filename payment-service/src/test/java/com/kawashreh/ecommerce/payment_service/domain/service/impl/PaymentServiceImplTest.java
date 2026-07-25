package com.kawashreh.ecommerce.payment_service.domain.service.impl;

import com.kawashreh.ecommerce.payment_service.dataAccess.dao.PaymentRepository;
import com.kawashreh.ecommerce.payment_service.dataAccess.entity.PaymentEntity;
import com.kawashreh.ecommerce.payment_service.domain.exception.OrderServiceException;
import com.kawashreh.ecommerce.payment_service.domain.model.Payment;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.client.OrderServiceClient;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderDto;
import com.kawashreh.ecommerce.payment_service.infrastructure.http.dto.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit tests for PaymentServiceImpl.processPayment - specifically covering
 * issue #10 (amount was always persisted as ZERO because it was never derived from the
 * order). These run without Docker/Spring context - no Testcontainers involved.
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
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.processPayment(orderId, buyerId, "CREDIT_CARD");

        // 2 * 10.00 + 3 * 5.50 = 36.50
        assertThat(result.getAmount()).isEqualByComparingTo("36.50");

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    void processPayment_throws_whenOrderNotFound_andNeverPersistsAZeroAmountPayment() {
        when(orderServiceClient.retrieveOrder(orderId)).thenReturn(null);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class)
                .hasMessageContaining(orderId.toString());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_throws_whenOrderServiceCallFails_andNeverPersistsAZeroAmountPayment() {
        when(orderServiceClient.retrieveOrder(orderId))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isInstanceOf(OrderServiceException.class);

        verify(paymentRepository, never()).save(any());
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

        verify(paymentRepository, never()).save(any());
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

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_doesNotWrapAnAlreadyThrownOrderServiceException() {
        OrderServiceException original = new OrderServiceException("Order not found", 404);
        when(orderServiceClient.retrieveOrder(orderId)).thenThrow(original);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, buyerId, "CREDIT_CARD"))
                .isSameAs(original);
    }
}
