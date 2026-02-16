package com.order_service.dataAccess.mapper;

import com.order_service.dataAccess.entity.CartItemEntity;
import com.order_service.domain.model.CartItem;

import java.util.List;

public final class CartItemMapper {

    public static CartItemEntity toEntity(CartItem d) {
        if (d == null) return null;
        return CartItemEntity.builder()
                .id(d.getId())
                .productId(d.getProductId())
                .productVariantId(d.getProductVariantId())
                .storeId(d.getStoreId())
                .productSku(d.getProductSku())
                .productName(d.getProductName())
                .quantity(d.getQuantity())
                .unitPrice(d.getUnitPrice())
                .lineTotal(d.getLineTotal())
                .currency(d.getCurrency())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    public static CartItem toDomain(CartItemEntity e) {
        if (e == null) return null;
        return CartItem.builder()
                .id(e.getId())
                .cartId(e.getCart() != null ? e.getCart().getId() : null)
                .productId(e.getProductId())
                .productVariantId(e.getProductVariantId())
                .storeId(e.getStoreId())
                .productSku(e.getProductSku())
                .productName(e.getProductName())
                .quantity(e.getQuantity())
                .unitPrice(e.getUnitPrice())
                .lineTotal(e.getLineTotal())
                .currency(e.getCurrency())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static List<CartItem> toDomainList(List<CartItemEntity> list) {
        if (list == null) return null;
        return list.stream()
                .map(CartItemMapper::toDomain)
                .toList();
    }

    public static List<CartItemEntity> toEntityList(List<CartItem> list) {
        if (list == null) return null;
        return list.stream()
                .map(CartItemMapper::toEntity)
                .toList();
    }
}
