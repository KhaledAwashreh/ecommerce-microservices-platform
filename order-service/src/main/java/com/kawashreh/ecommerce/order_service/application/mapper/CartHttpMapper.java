package com.kawashreh.ecommerce.order_service.application.mapper;

import com.kawashreh.ecommerce.order_service.application.dto.CartItemDto;
import com.kawashreh.ecommerce.order_service.application.dto.CartDto;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;
import com.kawashreh.ecommerce.order_service.domain.model.CartItem;

import java.util.List;

public final class CartHttpMapper {

    private CartHttpMapper() {} // Prevent instantiation

    // Cart mappers
    public static CartDto toDto(Cart cart) {
        if (cart == null) return null;

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .status(cart.getStatus())
                .cartItems(cartItemsToDtos(cart.getCartItems()))
                .subtotal(cart.getSubtotal())
                .discountTotal(cart.getDiscountTotal())
                .taxTotal(cart.getTaxTotal())
                .shippingTotal(cart.getShippingTotal())
                .totalPrice(cart.getTotalPrice())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .createdBy(cart.getCreatedBy())
                .updatedBy(cart.getUpdatedBy())
                .build();
    }

    public static Cart toDomain(CartDto dto) {
        if (dto == null) return null;

        return Cart.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .sessionId(dto.getSessionId())
                .status(dto.getStatus())
                .cartItems(cartItemsDtosToDomain(dto.getCartItems()))
                .subtotal(dto.getSubtotal())
                .discountTotal(dto.getDiscountTotal())
                .taxTotal(dto.getTaxTotal())
                .shippingTotal(dto.getShippingTotal())
                .totalPrice(dto.getTotalPrice())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .build();
    }

    public static List<CartDto> toDtoList(List<Cart> carts) {
        if (carts == null) return null;
        return carts.stream()
                .map(CartHttpMapper::toDto)
                .toList();
    }

    // CartItem mappers
    public static CartItemDto toDto(CartItem item) {
        if (item == null) return null;

        return CartItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productVariantId(item.getProductVariantId())
                .storeId(item.getStoreId())
                .productSku(item.getProductSku())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .currency(item.getCurrency())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public static CartItem toDomain(CartItemDto dto) {
        if (dto == null) return null;

        return CartItem.builder()
                .id(dto.getId())
                .productId(dto.getProductId())
                .productVariantId(dto.getProductVariantId())
                .storeId(dto.getStoreId())
                .productSku(dto.getProductSku())
                .productName(dto.getProductName())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .lineTotal(dto.getLineTotal())
                .currency(dto.getCurrency())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    // Helper methods
    private static List<CartItemDto> cartItemsToDtos(List<CartItem> items) {
        if (items == null) return null;
        return items.stream()
                .map(CartHttpMapper::toDto)
                .toList();
    }

    private static List<CartItem> cartItemsDtosToDomain(List<CartItemDto> dtos) {
        if (dtos == null) return null;
        return dtos.stream()
                .map(CartHttpMapper::toDomain)
                .toList();
    }
}
