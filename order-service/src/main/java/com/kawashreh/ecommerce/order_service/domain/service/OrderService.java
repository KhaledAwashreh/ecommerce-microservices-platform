package com.kawashreh.ecommerce.order_service.domain.service;

import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    Order create(Order order);

    List<Order> getAll();

    Order findById(UUID id);

    List<Order> findByBuyer(UUID buyerId);

    List<Order> findBySeller(UUID sellerId);

    List<Order> findByStoreId(UUID storeId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByBuyerAndStoreId(UUID buyerId, UUID storeId);

    List<Order> findBySellerAndStoreId(UUID sellerId, UUID storeId);

    List<Order> findByBuyerAndStatus(UUID buyerId, OrderStatus status);

    Order update(Order order);

    void delete(UUID id);
}
