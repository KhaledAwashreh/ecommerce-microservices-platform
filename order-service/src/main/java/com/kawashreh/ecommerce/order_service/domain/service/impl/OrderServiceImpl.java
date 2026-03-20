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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order create(Order order) {
        validateInventoryAvailability(order);

        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity);

        try {
            updateProductInventory(order);

            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved);
            logger.info("Order {} created and confirmed successfully", confirmed.getId());
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved);
            logger.error("Order {} creation failed during inventory update. Order marked as CANCELLED", saved.getId(), e);
            throw new RuntimeException("Order creation failed: Unable to update inventory - distributed transaction rolled back", e);
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

    private void updateProductInventory(Order order) {
        for (OrderItem item : order.getSelectedItems()) {
            try {
                boolean deducted = productServiceClient.deductInventory(item.getProductSku(), item.getQuantity());
                if (!deducted) {
                    logger.error("Failed to deduct inventory for product: {}", item.getProductSku());
                    throw new RuntimeException("Failed to deduct inventory for product: " + item.getProductSku());
                }
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

    @Transactional(rollbackFor = Exception.class)
    public Order createOrderFromCart(UUID cartId, UUID buyer) {
        logger.info("Creating order from cart: {} for buyer: {}", cartId, buyer);

        Order order = convertCartToOrder(cartId, buyer);

        validateInventoryAvailability(order);

        var entity = OrderMapper.toEntity(order);
        entity.getSelectedItems().forEach(item -> item.setOrder(entity));
        entity.setStatus(OrderStatus.PENDING);
        var saved = repository.save(entity);

        try {
            updateProductInventory(order);

            saved.setStatus(OrderStatus.CONFIRMED);
            var confirmed = repository.save(saved);
            logger.info("Order {} created from cart {} and confirmed successfully", confirmed.getId(), cartId);
            return OrderMapper.toDomain(confirmed);
        } catch (Exception e) {
            saved.setStatus(OrderStatus.CANCELLED);
            repository.save(saved);
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