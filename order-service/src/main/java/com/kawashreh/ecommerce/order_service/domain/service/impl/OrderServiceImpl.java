package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderEntity;
import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderItemEntity;
import com.kawashreh.ecommerce.order_service.dataAccess.mapper.OrderMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.exception.InsufficientStockException;
import com.kawashreh.ecommerce.order_service.domain.exception.InvalidOrderStateException;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.PaymentClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    // Issue #9: Order domain model carries no payment-method field today (frontend's
    // checkout form collects one, but never threads it through to order-service - see
    // ai_docs/order-service.md). Adding that field is a schema/DTO change across module
    // boundaries and out of scope for wiring payment orchestration itself, so a fixed
    // placeholder is sent instead; payment-service treats paymentMethod as free text with
    // no validation/branching on it.
    private static final String DEFAULT_PAYMENT_METHOD = "CARD";

    // GH #43: the legal order-status transition graph. PENDING and CONFIRMED can each
    // still be cancelled; SHIPPED can only move forward to DELIVERED; DELIVERED and
    // CANCELLED are terminal - nothing may transition out of them.
    private static final Map<OrderStatus, Set<OrderStatus>> LEGAL_STATUS_TRANSITIONS = new EnumMap<>(OrderStatus.class);
    static {
        LEGAL_STATUS_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        LEGAL_STATUS_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        LEGAL_STATUS_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        LEGAL_STATUS_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        LEGAL_STATUS_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository repository;
    private final ProductServiceClient productServiceClient;
    private final PaymentClient paymentClient;

    public OrderServiceImpl(OrderRepository repository, ProductServiceClient productServiceClient, PaymentClient paymentClient) {
        this.repository = repository;
        this.productServiceClient = productServiceClient;
        this.paymentClient = paymentClient;
    }

    // NOT_SUPPORTED overrides the class-level @Transactional so that no DB transaction is
    // held for the duration of this method. The remote Feign calls (validate/deduct) run
    // with no ambient transaction; each repository.save() below is invoked directly on the
    // repository bean (never via a self-invoked @Transactional helper on `this`, which
    // would silently bypass the proxy), so Spring Data's own per-method @Transactional on
    // SimpleJpaRepository opens and commits an independent transaction for every call.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order create(Order order) {
        validateInventoryAvailability(order);

        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity); // own transaction, commits immediately

        // Issue #7: populated by updateProductInventory as each item's deduction succeeds,
        // so that on partial failure we compensate EXACTLY the items that were actually
        // deducted - not the whole order, not a guess.
        List<OrderItemEntity> deductedItems = new ArrayList<>();
        // Issue #9: tracks whether invokePayment returned successfully (buyer was actually
        // charged) so the catch block below can tell "failed before any charge happened"
        // (safe to restore inventory and cancel) apart from "charge succeeded, but the
        // CONFIRMED save itself then failed" (must NOT restore inventory or silently
        // cancel - see the branch below).
        boolean paymentCompleted = false;
        try {
            updateProductInventory(saved, deductedItems); // remote calls only, no DB transaction held

            // Issue #9: order-service orchestrates payment using the previously-unused
            // PaymentClient. `saved` was already committed as PENDING in its own
            // transaction above, so payment-service's callback to GET
            // /api/v1/orders/{id} (issue #10, to derive the authoritative amount) reads a
            // row that is already durable - no circular-call deadlock/404 here.
            invokePayment(saved.getId(), saved.getBuyer());
            paymentCompleted = true;

            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved); // own transaction, commits
            logger.info("Order {} created and confirmed successfully", confirmed.getId());
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            if (paymentCompleted) {
                // Issue #9: the buyer was already charged (invokePayment returned
                // successfully) and only the subsequent CONFIRMED save failed. Restoring
                // inventory here would be wrong - the goods were legitimately sold and
                // paid for - and silently falling through to the CANCELLED branch below
                // would produce a charged-but-cancelled order, the worst possible outcome.
                // There is no automatic recovery available from this method for a local DB
                // write failing right after an external charge already succeeded, so this
                // is surfaced as loudly as possible for manual reconciliation instead of
                // silently compensating the wrong side.
                logger.error("CRITICAL: payment for order {} completed successfully but the order could not be " +
                                "persisted as CONFIRMED afterward - inventory was NOT restored (goods were " +
                                "legitimately sold) and the order was NOT marked CANCELLED - requires immediate " +
                                "manual reconciliation to avoid a charged-but-unconfirmed order",
                        saved.getId(), e);
                throw new RuntimeException("Order confirmation failed after payment succeeded for order " +
                        saved.getId() + " - manual reconciliation required", e);
            }

            // Issue #7 fix: restore exactly the inventory already deducted for this order
            // before marking it CANCELLED. restoreDeductedInventory never throws - a failed
            // restore is logged for manual reconciliation and must never mask or replace the
            // original failure `e`, which is always what the caller sees below.
            restoreDeductedInventory(deductedItems, saved.getId());
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved); // own transaction, commits and survives independently of the branch above
            logger.error("Order {} creation failed before payment completed. Order marked as CANCELLED", saved.getId(), e);
            throw new RuntimeException("Order creation failed: distributed transaction rolled back", e);
        }
    }

    /**
     * Issue #9: invokes payment-service via the previously-dead {@link PaymentClient} as
     * part of order creation. Runs with no DB transaction held (same as
     * {@link #updateProductInventory}), after inventory has been deducted and before the
     * order is flipped to CONFIRMED, so a payment failure lands in the exact same
     * catch/compensate block as an inventory-deduction failure in {@link #create}.
     *
     * <p>Idempotency note (issue #11): {@code PaymentClient.processPayment} is idempotent
     * per {@code orderId} on payment-service's side - a repeat call for an order that
     * already has a payment returns the existing (already-COMPLETED) row instead of
     * charging twice. That makes it safe to call this again for the same order id (e.g. if
     * a future retry policy were added to the payment-service Feign client, which today it
     * is not - see ai_docs). It does not, by itself, protect against the case where this
     * call times out after payment-service already completed the charge server-side but
     * the response never reached order-service: from here that is indistinguishable from
     * "payment never happened" and falls through to the restore/cancel path below. That is
     * an inherent limitation of synchronous orchestration without a saga/outbox, the same
     * class of gap already documented for the inventory-deduction call.
     *
     * @throws RuntimeException wrapping the original cause if the Feign call fails, returns
     *                          {@code null}, or reports a non-COMPLETED status.
     */
    private void invokePayment(UUID orderId, UUID buyerId) {
        try {
            PaymentDto request = PaymentDto.builder()
                    .orderId(orderId)
                    .buyerId(buyerId)
                    .paymentMethod(DEFAULT_PAYMENT_METHOD)
                    .build();

            PaymentDto response = paymentClient.processPayment(request);

            if (response == null || response.getStatus() != PaymentDto.PaymentStatus.COMPLETED) {
                throw new RuntimeException("Payment was not completed for order " + orderId +
                        " - status: " + (response == null ? "null response" : response.getStatus()));
            }

            logger.info("Payment {} completed for order {}", response.getId(), orderId);
        } catch (Exception e) {
            logger.error("Payment failed for order {}. Order transaction will be rolled back.", orderId, e);
            throw new RuntimeException("Payment failed for order " + orderId +
                    " - distributed transaction will be rolled back", e);
        }
    }

    /**
     * Compensating step for issue #7. Restores exactly the items in {@code deductedItems}
     * (populated by {@link #updateProductInventory} up to the point of failure) by calling
     * {@code ProductServiceClient.restoreInventory} for each.
     *
     * <p>Restore-failure semantics (deliberate choice, not the default): each item's restore
     * is attempted independently and any failure (thrown exception or a {@code false}/{@code
     * null} result) is logged at ERROR with enough detail (order id, product sku, quantity)
     * for manual reconciliation, then execution continues to the next item. Failures here are
     * intentionally swallowed with respect to the caller - they are never rethrown and never
     * allowed to replace or suppress the original inventory-update failure that triggered this
     * compensation; that original exception is what the caller of {@code create}/
     * {@code createOrderFromCart} always sees. This module has no retry queue or dead-letter
     * infrastructure today, so "log at ERROR for manual/alerted reconciliation" is the chosen
     * behavior rather than inline retry or dead-lettering; revisit if such infrastructure is
     * added.
     */
    private void restoreDeductedInventory(List<OrderItemEntity> deductedItems, UUID orderId) {
        for (OrderItemEntity item : deductedItems) {
            try {
                Boolean restored = productServiceClient.restoreInventory(item.getProductSku(), item.getId(), item.getQuantity());
                if (Boolean.TRUE.equals(restored)) {
                    logger.info("Restored {} units of product {} for cancelled order {}",
                            item.getQuantity(), item.getProductSku(), orderId);
                } else {
                    logger.error("CRITICAL: inventory restore returned {} for product {} (order {}, qty {}) - " +
                                    "stock leak, requires manual reconciliation",
                            restored, item.getProductSku(), orderId, item.getQuantity());
                }
            } catch (Exception restoreEx) {
                logger.error("CRITICAL: failed to restore {} units of product {} for cancelled order {} - " +
                                "inventory leak, requires manual reconciliation",
                        item.getQuantity(), item.getProductSku(), orderId, restoreEx);
            }
        }
    }

    private void validateInventoryAvailability(Order order) {
        if (order.getSelectedItems() == null || order.getSelectedItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        for (OrderItem item : order.getSelectedItems()) {
            try {
                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product == null) {
                    logger.error("Product not found: {}", item.getProductSku());
                    throw new IllegalArgumentException("Product not found: " + item.getProductSku());
                }

                InventoryDto inventory = productServiceClient.retrieveInventory(item.getProductSku());
                if (inventory == null) {
                    logger.error("Inventory not found for product: {}", item.getProductSku());
                    throw new IllegalArgumentException("Inventory not found for product: " + item.getProductSku());
                }

                int availableStock = inventory.getQuantity();
                if (availableStock < item.getQuantity()) {
                    logger.warn("Insufficient stock for product {}: requested {}, available {}",
                            item.getProductSku(), item.getQuantity(), availableStock);
                    throw new InsufficientStockException(
                            item.getProductSku().toString(),
                            item.getQuantity(),
                            availableStock);
                }

                logger.info("Inventory validation passed for product: {} - Quantity requested: {}",
                        item.getProductSku(), item.getQuantity());
                logger.info("Inventory validation passed for product: {} - Quantity requested: {}",
                        product.getId(), item.getQuantity());

            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Failed to validate inventory for product: {}", item.getProductSku(), e);
                throw new IllegalArgumentException("Unable to validate product availability: " + e.getMessage(), e);
            }
        }
    }

    // GH #30: iterates saved.getSelectedItems() (persisted OrderItemEntity, generated ids
    // populated by cascade-persist inside repository.save() above) rather than
    // order.getSelectedItems() (pre-save domain OrderItem, id always null at this point) -
    // product-service's deduction ledger needs a real, stable orderItemId to key on.
    private void updateProductInventory(OrderEntity savedEntity, List<OrderItemEntity> deductedItems) {
        for (OrderItemEntity item : savedEntity.getSelectedItems()) {
            try {
                boolean deducted = productServiceClient.deductInventory(item.getProductSku(), item.getId(), item.getQuantity());
                if (!deducted) {
                    logger.error("Failed to deduct inventory for product: {}", item.getProductSku());
                    throw new RuntimeException("Failed to deduct inventory for product: " + item.getProductSku());
                }
                // Track success immediately - this item is now deducted on product-service
                // regardless of what happens below (including the redundant retrieveProduct
                // check further down failing), so it must be compensated on any later failure.
                deductedItems.add(item);
                logger.info("Inventory deducted successfully for product: {} - Quantity: {}",
                        item.getProductSku(), item.getQuantity());

                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product != null) {
                    logger.info("Deducting {} units from product {} (SKU: {})",
                            item.getQuantity(), product.getId(), item.getProductSku());
                    logger.info("Inventory updated successfully for product: {}", product.getId());
                } else {
                    throw new RuntimeException("Product not found during inventory update: " + item.getProductSku());
                }
            } catch (Exception e) {
                logger.error("Failed to update inventory for product: {}. Order transaction will be rolled back.",
                        item.getProductSku(), e);
                throw new RuntimeException("Inventory update failed for product " + item.getProductSku() +
                        " - distributed transaction will be rolled back", e);
            }
        }
    }

    @Override
    public List<Order> getAll() {
        return repository.findAll()
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public Order findById(UUID id) {
        return repository.findById(id)
                .map(OrderMapper::toDomain)
                .orElse(null);
    }

    @Override
    public List<Order> findByBuyer(UUID buyerId) {
        return repository.findByBuyer(buyerId)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Order> findBySeller(UUID sellerId) {
        return repository.findBySeller(sellerId)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Order> findByStoreId(UUID storeId) {
        return repository.findByStoreId(storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return repository.findByStatus(status)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Order> findByBuyerAndStoreId(UUID buyerId, UUID storeId) {
        return repository.findByBuyerAndStoreId(buyerId, storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Order> findBySellerAndStoreId(UUID sellerId, UUID storeId) {
        return repository.findBySellerAndStoreId(sellerId, storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Order> findByBuyerAndStatus(UUID buyerId, OrderStatus status) {
        return repository.findByBuyerAndStatus(buyerId, status)
                .stream()
                .map(OrderMapper::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public Order update(Order order) {
        // GH #42: repository.save() on an id-supplied entity routes to merge(), which for a
        // non-existent id surfaces as a provider-specific error, not a clean 404. Guard with
        // an explicit existence check first, and reuse that lookup (GH #43) to validate the
        // requested status transition against the order's current persisted status.
        var existing = repository.findById(order.getId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + order.getId()));
        validateStatusTransition(existing.getStatus(), order.getStatus());

        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        var updated = repository.save(entity);
        return OrderMapper.toDomain(updated);
    }

    /**
     * GH #43: rejects a status update that is not a legal transition from the order's
     * current status per {@link #LEGAL_STATUS_TRANSITIONS} - e.g. moving backward from
     * CONFIRMED to PENDING, or skipping straight to SHIPPED/DELIVERED. A no-op (current
     * equals requested) is always allowed.
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus requested) {
        if (current == requested) {
            return;
        }
        Set<OrderStatus> allowedNextStates = LEGAL_STATUS_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
        if (!allowedNextStates.contains(requested)) {
            throw new InvalidOrderStateException(
                    "Cannot transition order status from " + current + " to " + requested);
        }
    }

    @Override
    public void delete(UUID id) {
        // GH #42: deleteById() throws EmptyResultDataAccessException (uncaught -> 500) for a
        // missing id. Guard with an explicit existence check so callers get a clean 404.
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Order not found: " + id);
        }
        repository.deleteById(id);
    }

    // Same NOT_SUPPORTED override as create() above, and for the same reason: suspend the
    // class-level @Transactional so remote calls run without holding a DB transaction, and
    // let each direct repository.save() call commit independently via the repository
    // bean's own proxy rather than a self-invoked @Transactional method on this bean.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order createOrderFromCart(UUID cartId, UUID buyer) {
        logger.info("Creating order from cart: {} for buyer: {}", cartId, buyer);

        Order order = convertCartToOrder(cartId, buyer);

        validateInventoryAvailability(order);

        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity); // own transaction, commits immediately

        // Issue #7: see create() above for the tracking/compensation rationale.
        List<OrderItemEntity> deductedItems = new ArrayList<>();
        try {
            updateProductInventory(saved, deductedItems); // remote calls only, no DB transaction held

            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved); // own transaction, commits
            logger.info("Order {} created from cart {} and confirmed successfully", confirmed.getId(), cartId);
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            // Issue #7 fix: same compensation as create() - restore exactly what was
            // deducted, never let a restore failure mask the original failure `e`.
            restoreDeductedInventory(deductedItems, saved.getId());
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved); // own transaction, commits and survives independently of the branch above
            logger.error("Order {} creation from cart {} failed during inventory update. Order marked as CANCELLED",
                    saved.getId(), cartId, e);
            throw new RuntimeException("Order creation from cart failed: Unable to update inventory - distributed transaction rolled back", e);
        }
    }

    private Order convertCartToOrder(UUID cartId, UUID buyer) {
        if (cartId == null || buyer == null) {
            throw new IllegalArgumentException("Cart ID and Buyer ID cannot be null");
        }

        Order order = Order.builder()
                .buyer(buyer)
                .status(OrderStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();

        logger.info("Cart {} converted to Order {}", cartId, order.getId());
        return order;
    }
}