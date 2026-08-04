package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderEntity;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.exception.InvalidOrderStateException;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.PaymentClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the issue #8 fix: {@code create}/{@code createOrderFromCart}
 * must persist a PENDING order, run the remote inventory-deduction call with no DB
 * transaction held, then persist either CONFIRMED or CANCELLED as an independent write.
 *
 * <p>Also covers the issue #7 fix: on partial-deduction failure, exactly the items already
 * deducted must be restored via {@code ProductServiceClient.restoreInventory} (not all
 * items, not none), and a failure in the restore call itself must never mask the original
 * inventory-update failure that the caller sees.</p>
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

    @Mock
    private PaymentClient paymentClient;

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
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(quantity)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Two-item order, used by the issue #7 partial-deduction/restore tests below - a
     * single-item order can never exercise "some items deducted, one fails" behavior.
     */
    private Order multiItemOrder(UUID sku1, int qty1, UUID sku2, int qty2) {
        return Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(sku1)
                                .quantity(qty1)
                                .unitPrice(BigDecimal.valueOf(9.99))
                                .build(),
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(sku2)
                                .quantity(qty2)
                                .unitPrice(BigDecimal.valueOf(19.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void stubHappyPathValidation(int availableQuantity) {
        InventoryDto inventory = InventoryDto.builder()
                .productVariationId(productVariationId)
                .quantity(availableQuantity)
                .build();

        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(inventory);
    }

    /**
     * Issue #9: stubs PaymentClient.processPayment to report a COMPLETED payment, the only
     * status payment-service ever actually produces (per ai_docs). Needed by any test whose
     * order gets far enough to reach the payment step (i.e. inventory deduction succeeds).
     */
    private void stubHappyPathPayment() {
        when(paymentClient.processPayment(any(PaymentDto.class))).thenAnswer(invocation -> {
            PaymentDto request = invocation.getArgument(0);
            return PaymentDto.builder()
                    .id(UUID.randomUUID())
                    .orderId(request.getOrderId())
                    .buyerId(request.getBuyerId())
                    .paymentMethod(request.getPaymentMethod())
                    .status(PaymentDto.PaymentStatus.COMPLETED)
                    .paymentGateway("SIMULATED")
                    .build();
        });
    }

    /**
     * Echoes back whatever OrderEntity is passed to save() and records the status AT THE
     * MOMENT OF THE CALL. This is necessary because the production code mutates and
     * reuses the same `saved` entity reference across both save() calls (PENDING ->
     * CONFIRMED/CANCELLED in place) - an ArgumentCaptor would just capture that one
     * mutable reference twice and see only its final state, so status must be
     * snapshotted eagerly inside the answer.
     * <p>
     * Also assigns a generated id (to the entity and, on the first call, each of its
     * items) if one isn't already set - real production code explicitly nulls both
     * before the first save() (see OrderServiceImpl.create()'s comment on why: a real,
     * client-facing OrderDto.id is always non-null, but the entity is @GeneratedValue and
     * a non-null id sends save() through merge() instead of persist(), corrupting a real
     * DB). A bare echo-back here would leave those ids null forever, since nothing else
     * in this test simulates what a real repository.save() does for a genuinely new row -
     * silently masking exactly the bug this comment describes, the same way the tests
     * originally missed it entirely.
     */
    private List<OrderStatus> stubSaveToReturnSameEntityAndRecordStatuses() {
        List<OrderStatus> savedStatuses = new ArrayList<>();
        when(repository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    OrderEntity e = invocation.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(UUID.randomUUID());
                    }
                    e.getSelectedItems().forEach(item -> {
                        if (item.getId() == null) {
                            item.setId(UUID.randomUUID());
                        }
                    });
                    savedStatuses.add(e.getStatus());
                    return e;
                });
        return savedStatuses;
    }

    @Test
    void create_savesPendingThenConfirmed_whenInventoryDeductionSucceeds() {
        stubHappyPathValidation(10);
        stubHappyPathPayment();
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);

        Order result = orderService.create(sampleOrder(2));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(repository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        verify(paymentClient, times(1)).processPayment(any(PaymentDto.class));
    }

    @Test
    void create_restoresDeductedInventoryAndCancels_whenPaymentFails() {
        // Issue #9: inventory deduction succeeds, but payment-service fails (e.g. Feign
        // error/timeout/circuit open). This must go down the exact same compensation path
        // as an inventory-deduction failure: restore the deducted stock and mark the order
        // CANCELLED, never leave stock deducted with no successful charge.
        stubHappyPathValidation(10);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);
        when(paymentClient.processPayment(any(PaymentDto.class)))
                .thenThrow(new RuntimeException("payment-service-unreachable"));

        Order order = sampleOrder(2);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order creation failed");

        verify(productServiceClient, times(1)).restoreInventory(eq(productVariationId), any(UUID.class), eq(2));
        verify(repository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void create_doesNotRestoreInventoryOrCancel_whenConfirmedSaveFailsAfterPaymentSucceeds() {
        // Issue #9: payment has already succeeded (buyer charged) by the time the CONFIRMED
        // save itself throws. Restoring inventory here would undo a legitimate sale, and
        // marking the order CANCELLED would produce a charged-but-cancelled order - the
        // worst outcome per the issue brief - so neither must happen; the failure must
        // instead surface loudly for manual reconciliation.
        stubHappyPathValidation(10);
        stubHappyPathPayment();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);
        when(repository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    // 1st call: PENDING insert succeeds. Must assign ids here, same as
                    // stubSaveToReturnSameEntityAndRecordStatuses() - production code nulls
                    // both the entity's and its items' ids before this call (real ids are
                    // @GeneratedValue), and updateProductInventory() below needs a non-null
                    // item id to call deductInventory with.
                    OrderEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.getSelectedItems().forEach(item -> item.setId(UUID.randomUUID()));
                    return e;
                })
                .thenThrow(new RuntimeException("db-unreachable"));  // 2nd call: CONFIRMED save fails

        Order order = sampleOrder(2);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("manual reconciliation required");

        verify(paymentClient, times(1)).processPayment(any(PaymentDto.class));
        verify(productServiceClient, never()).restoreInventory(any(UUID.class), any(UUID.class), anyInt());
        verify(repository, times(2)).save(any(OrderEntity.class));
    }

    @Test
    void create_savesPendingThenCancelled_andRethrows_whenInventoryDeductionFails() {
        stubHappyPathValidation(10);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(false);

        Order order = sampleOrder(2);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order creation failed");

        verify(repository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void create_restoresOnlyTheItemsActuallyDeducted_whenALaterItemFailsToDeduct() {
        // Issue #7: item 1 deducts successfully, item 2 fails. Only item 1's deduction
        // must be compensated - not item 2 (never deducted), not a blanket restore of the
        // whole order.
        UUID sku1 = UUID.randomUUID();
        UUID sku2 = UUID.randomUUID();
        stubHappyPathValidation(100);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(eq(sku1), any(UUID.class), eq(2))).thenReturn(true);
        when(productServiceClient.deductInventory(eq(sku2), any(UUID.class), eq(3))).thenReturn(false);

        Order order = multiItemOrder(sku1, 2, sku2, 3);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order creation failed");

        verify(productServiceClient, times(1)).restoreInventory(eq(sku1), any(UUID.class), eq(2));
        verify(productServiceClient, never()).restoreInventory(eq(sku2), any(UUID.class), anyInt());
        assertThat(savedStatuses).containsExactly(OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void create_stillThrowsOriginalFailure_whenTheRestoreCallItselfFails() {
        // Issue #7: the compensating restoreInventory call throwing must not mask or
        // replace the original inventory-update failure - the caller must still see the
        // original cause, and the order must still end up CANCELLED.
        UUID sku1 = UUID.randomUUID();
        UUID sku2 = UUID.randomUUID();
        stubHappyPathValidation(100);
        List<OrderStatus> savedStatuses = stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(eq(sku1), any(UUID.class), eq(2))).thenReturn(true);
        when(productServiceClient.deductInventory(eq(sku2), any(UUID.class), eq(3))).thenReturn(false);
        when(productServiceClient.restoreInventory(eq(sku1), any(UUID.class), eq(2)))
                .thenThrow(new RuntimeException("restore-service-unreachable"));

        Order order = multiItemOrder(sku1, 2, sku2, 3);

        assertThatThrownBy(() -> orderService.create(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order creation failed")
                .hasMessageNotContaining("restore-service-unreachable");

        verify(productServiceClient, times(1)).restoreInventory(eq(sku1), any(UUID.class), eq(2));
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
        verify(productServiceClient, never()).deductInventory(any(UUID.class), any(UUID.class), anyInt());
    }

    @Test
    void create_carriesShippingAddressIdThroughToTheConfirmedOrder() {
        // GH #58: the shipping address selected at checkout must survive the full
        // domain -> entity -> repository -> domain round trip performed by create().
        stubHappyPathValidation(10);
        stubHappyPathPayment();
        stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);

        UUID shippingAddressId = UUID.randomUUID();
        Order order = sampleOrder(2);
        order.setShippingAddressId(shippingAddressId);

        Order result = orderService.create(order);

        assertThat(result.getShippingAddressId()).isEqualTo(shippingAddressId);
    }

    @Test
    void create_allowsNullShippingAddressId_forBackwardCompatibility() {
        // GH #58: shippingAddressId is nullable - existing/other callers that don't supply
        // one (e.g. the dead createOrderFromCart path) must not be broken by this field's
        // addition.
        stubHappyPathValidation(10);
        stubHappyPathPayment();
        stubSaveToReturnSameEntityAndRecordStatuses();
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);

        Order order = sampleOrder(2);
        assertThat(order.getShippingAddressId()).isNull();

        Order result = orderService.create(order);

        assertThat(result.getShippingAddressId()).isNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
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
    void update_throwsNoSuchElement_whenOrderDoesNotExist() {
        // GH #42: update() must not call repository.save() unconditionally for an id that
        // doesn't exist - that either silently inserts a new row (id was client-supplied,
        // so isNew() is false and Spring Data routes to merge()) or blows up with a
        // provider-specific error, neither of which is the intended 404. Guard with an
        // existence check that raises a clean NoSuchElementException instead.
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        Order order = sampleOrder(1);
        order.setId(missingId);

        assertThatThrownBy(() -> orderService.update(order))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(missingId.toString());

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void delete_throwsNoSuchElement_whenOrderDoesNotExist() {
        // GH #42: delete() must not call repository.deleteById() unconditionally - Spring
        // Data throws EmptyResultDataAccessException for a missing id, which is unhandled
        // in this module and surfaces as a 500. Guard with an existence check instead.
        UUID missingId = UUID.randomUUID();
        when(repository.existsById(missingId)).thenReturn(false);

        assertThatThrownBy(() -> orderService.delete(missingId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(missingId.toString());

        verify(repository, never()).deleteById(any(UUID.class));
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

    // --- GH #43: order status transitions must be guarded --------------------------

    private OrderEntity existingEntityWithStatus(UUID orderId, OrderStatus status) {
        return OrderEntity.builder()
                .id(orderId)
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .status(status)
                .selectedItems(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void update_rejectsBackwardTransition_fromConfirmedToPending() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.of(existingEntityWithStatus(orderId, OrderStatus.CONFIRMED)));

        Order order = sampleOrder(2);
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING);

        assertThatThrownBy(() -> orderService.update(order))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void update_rejectsSkippingStates_fromPendingToShipped() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.of(existingEntityWithStatus(orderId, OrderStatus.PENDING)));

        Order order = sampleOrder(2);
        order.setId(orderId);
        order.setStatus(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> orderService.update(order))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void update_rejectsAnyTransition_outOfTerminalCancelledState() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.of(existingEntityWithStatus(orderId, OrderStatus.CANCELLED)));

        Order order = sampleOrder(2);
        order.setId(orderId);
        order.setStatus(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> orderService.update(order))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(repository, never()).save(any(OrderEntity.class));
    }

    @Test
    void update_allowsLegalForwardTransition_fromConfirmedToShipped() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.of(existingEntityWithStatus(orderId, OrderStatus.CONFIRMED)));
        when(repository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = sampleOrder(2);
        order.setId(orderId);
        order.setStatus(OrderStatus.SHIPPED);

        Order result = orderService.update(order);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(repository, times(1)).save(any(OrderEntity.class));
    }
}
