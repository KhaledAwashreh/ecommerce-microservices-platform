package com.kawashreh.ecommerce.frontend.facade;

import com.kawashreh.ecommerce.frontend.client.OrderServiceClient;
import com.kawashreh.ecommerce.frontend.client.PaymentServiceClient;
import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.PaymentResponseDto;
import com.kawashreh.ecommerce.frontend.dto.facade.OrderWithDetailsDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OrderFacade {

    private final OrderServiceClient orderServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public OrderFacade(OrderServiceClient orderServiceClient,
                       PaymentServiceClient paymentServiceClient) {
        this.orderServiceClient = orderServiceClient;
        this.paymentServiceClient = paymentServiceClient;
    }

    public List<OrderDto> getOrdersByBuyer(UUID buyerId) {
        try {
            return orderServiceClient.getOrdersByBuyer(buyerId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public OrderWithDetailsDto getOrderWithPayment(UUID orderId) {
        OrderDto order = null;
        PaymentResponseDto payment = null;

        try {
            order = orderServiceClient.getOrderById(orderId);
        } catch (Exception e) {
            // order stays null
        }

        if (order != null) {
            try {
                payment = paymentServiceClient.getPaymentByOrderId(orderId);
            } catch (Exception e) {
                // payment stays null
            }
        }

        return OrderWithDetailsDto.builder()
                .order(order)
                .payment(payment)
                .build();
    }

    public List<OrderWithDetailsDto> getOrdersWithPayments(UUID buyerId) {
        List<OrderDto> orders = getOrdersByBuyer(buyerId);
        return orders.stream()
                .map(order -> getOrderWithPayment(order.getId()))
                .collect(Collectors.toList());
    }

    public OrderDto createOrder(OrderDto order) {
        try {
            return orderServiceClient.createOrder(order);
        } catch (Exception e) {
            return null;
        }
    }
}