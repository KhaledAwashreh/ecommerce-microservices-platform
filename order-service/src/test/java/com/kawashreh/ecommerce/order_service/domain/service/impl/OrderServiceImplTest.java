package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderEntity;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the issue #8 fix: {@code create}/{@code createOrderFromCart}
 * must persist a PENDING order, run the remote inventory-deduction call with no DB
 * transaction held, then persist either CONFIRMED or CANCELLED as an independent write.
 *
 * These tests run WITHOUT Docker/Testcontainers and WITHOUT a Spring context, so they
 * cannot observe real transaction commit/rollback semantics (that requires a
 * PlatformTransactionManager, which only exists under @SpringBootTest). What they verify
 * instead:
 *   1. The code path itself - PENDING is saved before the remote call, CANCELLED is saved
 *      via a plain (non-self-invoked) repository.save() call after a failure, and the
 *      method still rethrows after that save (matching the required control flow).
 *   2. Via reflection, that both create() and createOrderFromCart() are annotated
 *      @Transactional(propagation = Propagation.NOT_SUPPORTED) - the mechanism that keeps
 *      the class-level @Transactional from wrapping the remote calls, and that removes any
 *      reliance on self-invoked @Transactional helper methods (which would silently skip
 *      the proxy on this bean).
 *
 * The actual "CANCELLED survives even though PENDING's insert already committed
 * separately" guarantee can only be confirmed with a real transaction manager
 * (@SpringBootTest + Testcontainers) - not exercised here since Docker is unavailable in
 * this environment.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID productVariationId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID storeId;

    @BeforeEach
    void setUp() {
        productVariationId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
    }

    private Order sampleOrder(int quantity) {
        return Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(quantity)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void stubHappyPathValidation(int availableQuantity) {
        ProductDto product = ProductDto.builder().id(productVariationId).build();
        InventoryDto inventory = InventoryDto.builder()
                .productVariationId(productVariationId)
                .quantity(availableQuantity)
                .reservedQuantity(0)
                .build();

        when(productServiceClient.retrieveProduct(any(UUID.class))).thenReturn(product);
        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(inventory);
    }

    /**
     * Echoes back whatever OrderEntity is passed to save() (like a real repository would
     * for an unmanaged entity) and records the status AT THE MOMENT OF THE CALL. This is
     * necessary because the production code mutates and reuses the same `saved` entity
     * reference across both save() calls (PENDING -> CONFIRMED/CANCELLED in place) - an
     * ArgumentCaptor would just capture that one mutable reference twice and see only its
     * final state, so status must be snapshotted eagerly inside the answer.
     */
    private List<OrderStatus> stubSaveToReturnSameEntityAndRecordStatuses() {
        List<OrderStatus> savedStatuses = new ArrayList<>();
        when(repository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    OrderEntity e = invocation.getArgument(0);
                    savedStatuses.add(e.getStatus());
                    return e;
                });
        return savedStatuses;
    }

    @Test
    void create_savesPendingThenConfirmed_whenInventoryDeductionSucceeds() {
        stubHappyPathValidation(10);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), anyInt())).thenReturn(true);

        Order result = orderService.create(sampleOrder(2));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(repository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    @Test
    void create_savesPendingThenCancelled_andRethrows_whenInventoryDeductionFails() {
        stubHappyPathValidation(10);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), anyInt())).thenReturn(false);

        Order order = sampleOrder(2);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order creation failed");

        verify(repository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void create_neverPersists_whenValidationFailsBeforeAnyRemoteDeduction() {
        // Insufficient stock: available (1) < requested (5). validateInventoryAvailability
        // must reject before any repository.save() or deductInventory call is made.
        stubHappyPathValidation(1);
        Order order = sampleOrder(5);

        assertThatThrownBy(() -> orderService.create(order))
                .hasMessageContaining("Insufficient stock");

        verify(repository, never()).save(any());
        verify(productServiceClient, never()).deductInventory(any(UUID.class), anyInt());
    }

    @Test
    void create_isAnnotatedNotSupported_soClassLevelTransactionalNeverWrapsRemoteCalls() throws NoSuchMethodException {
        Method create = OrderServiceImpl.class.getMethod("create", Order.class);
        Transactional tx = create.getAnnotation(Transactional.class);

        assertThat(tx)
                .as("create() must override the class-level @Transactional so the proxy does not " +
                        "hold a DB transaction open across the remote inventory calls")
                .isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void createOrderFromCart_isAnnotatedNotSupported_sameAsCreate() throws NoSuchMethodException {
        // createOrderFromCart is not on the OrderService interface (dead/unreachable per
        // ai_docs Gotcha #7) and its helper convertCartToOrder always builds an Order with
        // an empty selectedItems list, so validateInventoryAvailability rejects it before
        // any save/deduct call regardless of this fix. Behavioral (CONFIRMED/CANCELLED)
        // coverage identical to create()'s tests above is therefore not obtainable without
        // also fixing that separate, out-of-scope bug. This test locks in the one thing
        // that IS meaningful here: the same transaction-boundary annotation is applied.
        Method createOrderFromCart = OrderServiceImpl.class.getMethod("createOrderFromCart", UUID.class, UUID.class);
        Transactional tx = createOrderFromCart.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
