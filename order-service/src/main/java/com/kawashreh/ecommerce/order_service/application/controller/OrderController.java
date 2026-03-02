package com.kawashreh.ecommerce.order_service.application.controller;

import com.kawashreh.ecommerce.order_service.application.dto.OrderDto;
import com.kawashreh.ecommerce.order_service.application.mapper.OrderHttpMapper;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ORDER_BASE)
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto orderDto) {
        var order = OrderHttpMapper.toDomain(orderDto);
        var created = orderService.create(order);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderHttpMapper.toDto(created));
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getAllOrders() {
        var orders = orderService.getAll();
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable UUID id) {
        var order = orderService.findById(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OrderHttpMapper.toDto(order));
    }

    @GetMapping(ApiPaths.ORDER_BY_BUYER)
    public ResponseEntity<List<OrderDto>> getOrdersByBuyer(@PathVariable UUID buyerId) {
        var orders = orderService.findByBuyer(buyerId);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_SELLER)
    public ResponseEntity<List<OrderDto>> getOrdersBySeller(@PathVariable UUID sellerId) {
        var orders = orderService.findBySeller(sellerId);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_STORE)
    public ResponseEntity<List<OrderDto>> getOrdersByStore(@PathVariable UUID storeId) {
        var orders = orderService.findByStoreId(storeId);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_STATUS)
    public ResponseEntity<List<OrderDto>> getOrdersByStatus(@PathVariable OrderStatus status) {
        var orders = orderService.findByStatus(status);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_BUYER_AND_STORE)
    public ResponseEntity<List<OrderDto>> getOrdersByBuyerAndStore(
            @PathVariable UUID buyerId,
            @PathVariable UUID storeId) {
        var orders = orderService.findByBuyerAndStoreId(buyerId, storeId);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_SELLER_AND_STORE)
    public ResponseEntity<List<OrderDto>> getOrdersBySellerAndStore(
            @PathVariable UUID sellerId,
            @PathVariable UUID storeId) {
        var orders = orderService.findBySellerAndStoreId(sellerId, storeId);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @GetMapping(ApiPaths.ORDER_BY_BUYER_AND_STATUS)
    public ResponseEntity<List<OrderDto>> getOrdersByBuyerAndStatus(
            @PathVariable UUID buyerId,
            @PathVariable OrderStatus status) {
        var orders = orderService.findByBuyerAndStatus(buyerId, status);
        return ResponseEntity.ok(OrderHttpMapper.toDtoList(orders));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderDto> updateOrder(
            @PathVariable UUID id,
            @RequestBody OrderDto orderDto) {
        var order = OrderHttpMapper.toDomain(orderDto);
        order.setId(id);
        var updated = orderService.update(order);
        return ResponseEntity.ok(OrderHttpMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

