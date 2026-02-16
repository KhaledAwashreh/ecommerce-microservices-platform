package com.order_service.domain.service;

import com.order_service.domain.enums.CartStatus;
import com.order_service.domain.model.Cart;
import com.order_service.domain.model.CartItem;

import java.util.List;
import java.util.UUID;

public interface CartService {

    Cart create(Cart cart);

    Cart findById(UUID id);

    Cart findByUserId(UUID userId);

    Cart findBySessionId(UUID sessionId);

    Cart findByUserIdAndStatus(UUID userId, CartStatus status);

    Cart findBySessionIdAndStatus(UUID sessionId, CartStatus status);

    List<Cart> findByStatus(CartStatus status);

    Cart addItem(UUID cartId, CartItem item);

    Cart removeItem(UUID cartId, UUID itemId);

    Cart updateItem(UUID cartId, CartItem item);

    Cart clearCart(UUID cartId);

    Cart update(Cart cart);

    void delete(UUID id);

    Cart recalculateTotals(UUID cartId);
}
