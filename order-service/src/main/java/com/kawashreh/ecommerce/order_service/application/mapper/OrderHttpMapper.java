package com.kawashreh.ecommerce.order_service.application.mapper;

import com.kawashreh.ecommerce.order_service.application.dto.DiscountDto;
import com.kawashreh.ecommerce.order_service.application.dto.OrderItemDto;
import com.kawashreh.ecommerce.order_service.domain.model.Discount;
import com.kawashreh.ecommerce.order_service.application.dto.OrderDto;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;

import java.util.List;

public final class OrderHttpMapper {

    private OrderHttpMapper() {} // Prevent instantiation

    // Order mappers
    public static OrderDto toDto(Order order) {
        if (order == null) return null;

        return OrderDto.builder()
                .id(order.getId())
                .storeId(order.getStoreId())
                .seller(order.getSeller())
                .buyer(order.getBuyer())
                .status(order.getStatus())
                .selectedItems(orderItemsToDtos(order.getSelectedItems()))
                .discountsApplied(discountsToDtos(order.getDiscountsApplied()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .createdBy(order.getCreatedBy())
                .updatedBy(order.getUpdatedBy())
                .build();
    }

    public static Order toDomain(OrderDto dto) {
        if (dto == null) return null;

        return Order.builder()
                .id(dto.getId())
                .storeId(dto.getStoreId())
                .seller(dto.getSeller())
                .buyer(dto.getBuyer())
                .status(dto.getStatus())
                .selectedItems(orderItemsDtosToDomain(dto.getSelectedItems()))
                .discountsApplied(discountsDtosToDomain(dto.getDiscountsApplied()))
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .build();
    }

    public static List<OrderDto> toDtoList(List<Order> orders) {
        if (orders == null) return null;
        return orders.stream()
                .map(OrderHttpMapper::toDto)
                .toList();
    }

    // OrderItem mappers
    public static OrderItemDto toDto(OrderItem item) {
        if (item == null) return null;

        return OrderItemDto.builder()
                .id(item.getId())
                .productSku(item.getProductSku())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .createdBy(item.getCreatedBy())
                .updatedBy(item.getUpdatedBy())
                .build();
    }

    public static OrderItem toDomain(OrderItemDto dto) {
        if (dto == null) return null;

        return OrderItem.builder()
                .id(dto.getId())
                .productSku(dto.getProductSku())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .build();
    }

    // Discount mappers
    public static DiscountDto toDto(Discount discount) {
        if (discount == null) return null;

        return DiscountDto.builder()
                .id(discount.getId())
                .name(discount.getName())
                .code(discount.getCode())
                .description(discount.getDescription())
                .createdAt(discount.getCreatedAt())
                .updatedAt(discount.getUpdatedAt())
                .createdBy(discount.getCreatedBy())
                .updatedBy(discount.getUpdatedBy())
                .build();
    }

    public static Discount toDomain(DiscountDto dto) {
        if (dto == null) return null;

        return Discount.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .build();
    }

    // Helper methods
    private static List<OrderItemDto> orderItemsToDtos(List<OrderItem> items) {
        if (items == null) return null;
        return items.stream()
                .map(OrderHttpMapper::toDto)
                .toList();
    }

    private static List<OrderItem> orderItemsDtosToDomain(List<OrderItemDto> dtos) {
        if (dtos == null) return null;
        return dtos.stream()
                .map(OrderHttpMapper::toDomain)
                .toList();
    }

    private static List<DiscountDto> discountsToDtos(List<Discount> discounts) {
        if (discounts == null) return null;
        return discounts.stream()
                .map(OrderHttpMapper::toDto)
                .toList();
    }

    private static List<Discount> discountsDtosToDomain(List<DiscountDto> dtos) {
        if (dtos == null) return null;
        return dtos.stream()
                .map(OrderHttpMapper::toDomain)
                .toList();
    }
}
