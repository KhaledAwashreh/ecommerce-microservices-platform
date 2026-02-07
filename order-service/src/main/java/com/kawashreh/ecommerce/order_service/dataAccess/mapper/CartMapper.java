package com.kawashreh.ecommerce.order_service.dataAccess.mapper;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.CartEntity;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;

import java.util.List;

public final class CartMapper {

    public static CartEntity toEntity(Cart d) {
        if (d == null) return null;
        return CartEntity.builder()
                .id(d.getId())
                .userId(d.getUserId())
                .sessionId(d.getSessionId())
                .status(d.getStatus())
                .cartItems(CartItemMapper.toEntityList(d.getCartItems()))
                .subtotal(d.getSubtotal())
                .discountTotal(d.getDiscountTotal())
                .taxTotal(d.getTaxTotal())
                .shippingTotal(d.getShippingTotal())
                .totalPrice(d.getTotalPrice())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .createdBy(d.getCreatedBy())
                .updatedBy(d.getUpdatedBy())
                .build();
    }

    public static Cart toDomain(CartEntity e) {
        if (e == null) return null;
        return Cart.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .sessionId(e.getSessionId())
                .status(e.getStatus())
                .cartItems(CartItemMapper.toDomainList(e.getCartItems()))
                .subtotal(e.getSubtotal())
                .discountTotal(e.getDiscountTotal())
                .taxTotal(e.getTaxTotal())
                .shippingTotal(e.getShippingTotal())
                .totalPrice(e.getTotalPrice())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .createdBy(e.getCreatedBy())
                .updatedBy(e.getUpdatedBy())
                .build();
    }

    public static List<Cart> toDomainList(List<CartEntity> list) {
        if (list == null) return null;
        return list.stream()
                .map(CartMapper::toDomain)
                .toList();
    }

    public static List<CartEntity> toEntityList(List<Cart> list) {
        if (list == null) return null;
        return list.stream()
                .map(CartMapper::toEntity)
                .toList();
    }
}
