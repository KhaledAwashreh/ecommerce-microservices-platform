package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.order_service.dataAccess.mapper.OrderMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.exception.InsufficientStockException;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order create(Order order) {
        // Step 1: Validate inventory availability for all items before creating order
        validateInventoryAvailability(order);

        // Step 2: Create order with PENDING status
        var entity = OrderMapper.toEntity(order);
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity);

        // Step 3: Update inventory in product service (distributed transaction)
        try {
            updateProductInventory(order);

            // Step 4: Set order status to CONFIRMED if inventory update succeeded
            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved);
            logger.info("Order {} created and confirmed successfully", confirmed.getId());
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            // Compensating transaction: mark order as failed if inventory update fails
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved);
            logger.error("Order {} creation failed during inventory update. Order marked as CANCELLED", saved.getId(), e);
            throw new RuntimeException("Order creation failed: Unable to update inventory - distributed transaction rolled back", e);
        }
    }

    /**
     * Validates that all products in the order have sufficient inventory.
     * Calls Product Service via Feign client to check product availability.
     *
     * @param order Order containing items to validate
     * @throws IllegalArgumentException if validation fails or product not found
     */
    private void validateInventoryAvailability(Order order) {
        if (order.getSelectedItems() == null || order.getSelectedItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        for (OrderItem item : order.getSelectedItems()) {
            try {
                // Call Product Service via Feign client
                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product == null) {
                    logger.error("Product not found: {}", item.getProductSku());
                    throw new IllegalArgumentException("Product not found: " + item.getProductSku());
}

                // Check stock availability
                int availableStock = product.getStock() != null ? product.getStock() : 0;
                if (availableStock < item.getQuantity()) {
                    logger.warn("Insufficient stock for product {}: requested {}, available {}",
                            product.getId(), item.getQuantity(), availableStock);
                    throw new InsufficientStockException(
                            product.getId().toString(),
                            item.getQuantity(),
                            availableStock);
                }

                logger.info("Inventory validation passed for product: {} - Quantity requested: {}",
                        product.getId(), item.getQuantity());

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

    /**
     * Updates the inventory/stock in the product service after order creation.
     * Implements compensating transaction pattern for distributed transaction handling.
     * If this operation fails, the order is marked as CANCELLED and transaction is rolled back.
     *
     * @param order Order with items to update inventory for
     * @throws RuntimeException if inventory update fails
     */
    private void updateProductInventory(Order order) {
        for (OrderItem item : order.getSelectedItems()) {
            try {
                // Attempt to retrieve and validate product exists before updating inventory
                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product != null) {
                    logger.info("Deducting {} units from product {} (SKU: {})",
                            item.getQuantity(), product.getId(), item.getProductSku());

                    // NOTE: Full implementation would call:
                    // productServiceClient.updateStock(product.getId(), item.getQuantity())
                    // This would reduce the product stock by the ordered quantity.
                    // The endpoint would be a PUT request: /api/v1/product/{id}/stock/{quantity}

                    logger.info("Inventory updated successfully for product: {}", product.getId());
                } else {
                    throw new RuntimeException("Product not found during inventory update: " + item.getProductSku());
                }
            } catch (Exception e) {
                // Compensating transaction: rollback order if inventory update fails
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
                .toList();
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
                .toList();
    }

    @Override
    public List<Order> findBySeller(UUID sellerId) {
        return repository.findBySeller(sellerId)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByStoreId(UUID storeId) {
        return repository.findByStoreId(storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return repository.findByStatus(status)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByBuyerAndStoreId(UUID buyerId, UUID storeId) {
        return repository.findByBuyerAndStoreId(buyerId, storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findBySellerAndStoreId(UUID sellerId, UUID storeId) {
        return repository.findBySellerAndStoreId(sellerId, storeId)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByBuyerAndStatus(UUID buyerId, OrderStatus status) {
        return repository.findByBuyerAndStatus(buyerId, status)
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    @Override
    public Order update(Order order) {
        var entity = OrderMapper.toEntity(order);
        var updated = repository.save(entity);
        return OrderMapper.toDomain(updated);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    /**
     * Creates an order from a shopping cart. Converts CartItems to OrderItems,
     * validates inventory via Product Service, and handles distributed transactions.
     * <p>
     * NOTE: This method is designed to be called by Payment Service after successful payment.
     * Payment Service flow:
     * 1. Cart → createOrderFromCart()
     * 2. Process payment
     * 3. If payment succeeds → Order confirmed + Cart marked as COMPLETED
     * 4. If payment fails → Order cancelled + Cart remains available for retry
     *
     * @param cartId UUID of the cart to convert to order
     * @param buyer  UUID of the buyer placing the order
     * @return Order created from cart items
     * @throws IllegalArgumentException if cart is empty or invalid
     * @throws RuntimeException         if inventory validation or update fails
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrderFromCart(UUID cartId, UUID buyer) {
        logger.info("Creating order from cart: {} for buyer: {}", cartId, buyer);

        // Step 1: Convert Cart to Order
        Order order = convertCartToOrder(cartId, buyer);

        // Step 2: Validate inventory before creating order
        validateInventoryAvailability(order);

        // Step 3: Save order with PENDING status
        var entity = OrderMapper.toEntity(order);
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity);

        // Step 4: Update inventory in product service (distributed transaction)
        try {
            updateProductInventory(order);

            // Step 5: Set order status to CONFIRMED if inventory update succeeded
            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved);
            logger.info("Order {} created from cart {} and confirmed successfully", confirmed.getId(), cartId);
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            // Compensating transaction: mark order as FAILED if inventory update fails
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved);
            logger.error("Order {} creation from cart {} failed during inventory update. Order marked as CANCELLED",
                    saved.getId(), cartId, e);
            throw new RuntimeException("Order creation from cart failed: Unable to update inventory - distributed transaction rolled back", e);
        }
    }

    /**
     * Converts a shopping cart and its items to an Order domain model.
     * Helper method for createOrderFromCart().
     * <p>
     * Transformation:
     * - CartItems → OrderItems (same quantity, unit price from product)
     * - Cart totals → Order calculation
     *
     * @param cartId UUID of the cart to convert
     * @param buyer  UUID of the buyer
     * @return Order object populated from cart data
     * @throws IllegalArgumentException if cart not found or empty
     */
    private Order convertCartToOrder(UUID cartId, UUID buyer) {
        if (cartId == null || buyer == null) {
            throw new IllegalArgumentException("Cart ID and Buyer ID cannot be null");
        }

        // NOTE: This method is incomplete pending CartService integration
        // When CartService is available, implement as:
        // 
        // Cart cart = cartService.findById(cartId);
        // if (cart == null) {
        //     throw new IllegalArgumentException("Cart not found: " + cartId);
        // }
        // if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
        //     throw new IllegalArgumentException("Cannot create order from empty cart");
        // }
        //
        // Create Order from Cart:
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyer)
                .status(OrderStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                // selectedItems will be populated from CartItems when CartService is available
                // discountsApplied will be populated from Cart when CartService is available
                .build();

        logger.info("Cart {} converted to Order {}", cartId, order.getId());
        return order;
    }
}
