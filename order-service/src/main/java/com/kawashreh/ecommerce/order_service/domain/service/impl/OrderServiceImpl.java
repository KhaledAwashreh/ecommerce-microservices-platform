package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.order_service.dataAccess.mapper.OrderMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository repository;

    public OrderServiceImpl(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order create(Order order) {
        var entity = OrderMapper.toEntity(order);
        var saved = repository.save(entity);
        return OrderMapper.toDomain(saved);
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
}
