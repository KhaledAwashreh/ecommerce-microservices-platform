package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.order_service.dataAccess.mapper.OrderMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.exception.InsufficientStockException;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository repository;
    private final ProductServiceClient productServiceClient;

    public OrderServiceImpl(OrderRepository repository, ProductServiceClient productServiceClient) {
        this.repository = repository;
        this.productServiceClient = productServiceClient;
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
        List<OrderItem> deductedItems = new ArrayList<>();
        try {
            updateProductInventory(order, deductedItems); // remote calls only, no DB transaction held

            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved); // own transaction, commits
            logger.info("Order {} created and confirmed successfully", confirmed.getId());
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            // Issue #7 fix: restore exactly the inventory already deducted for this order
            // before marking it CANCELLED. restoreDeductedInventory never throws - a failed
            // restore is logged for manual reconciliation and must never mask or replace the
            // original failure `e`, which is always what the caller sees below.
            restoreDeductedInventory(deductedItems, saved.getId());
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved); // own transaction, commits and survives independently of the branch above
            logger.error("Order {} creation failed during inventory update. Order marked as CANCELLED", saved.getId(), e);
            throw new RuntimeException("Order creation failed: Unable to update inventory - distributed transaction rolled back", e);
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
    private void restoreDeductedInventory(List<OrderItem> deductedItems, UUID orderId) {
        for (OrderItem item : deductedItems) {
            try {
                Boolean restored = productServiceClient.restoreInventory(item.getProductSku(), item.getQuantity());
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

                int availableStock = inventory.getAvailableQuantity();
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

    private void updateProductInventory(Order order, List<OrderItem> deductedItems) {
        for (OrderItem item : order.getSelectedItems()) {
            try {
                boolean deducted = productServiceClient.deductInventory(item.getProductSku(), item.getQuantity());
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
        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        var updated = repository.save(entity);
        return OrderMapper.toDomain(updated);
    }

    @Override
    public void delete(UUID id) {
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
        List<OrderItem> deductedItems = new ArrayList<>();
        try {
            updateProductInventory(order, deductedItems); // remote calls only, no DB transaction held

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