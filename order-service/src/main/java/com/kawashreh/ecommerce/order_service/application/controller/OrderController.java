package com.kawashreh.ecommerce.order_service.application.controller;

import com.kawashreh.ecommerce.order_service.application.dto.OrderDto;
import com.kawashreh.ecommerce.order_service.application.mapper.OrderHttpMapper;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.exception.InvalidOrderStateException;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ORDER_BASE)
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody @Valid OrderDto orderDto) {
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
            @RequestBody @Valid OrderDto orderDto) {
        var order = OrderHttpMapper.toDomain(orderDto);
        order.setId(id);
        try {
            var updated = orderService.update(order);
            return ResponseEntity.ok(OrderHttpMapper.toDto(updated));
        } catch (InvalidOrderStateException e) {
            // GH #43: the order exists but the requested status transition is not legal
            // from its current status - a genuine, deterministic client-side conflict, not
            // a server error. Mapped locally here rather than via a module-wide
            // GlobalExceptionHandler - this module has none (root CLAUDE.md) and a single
            // call site doesn't warrant adding one, mirroring PaymentController#refundPayment.
            logger.warn("Rejected status transition for order {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

