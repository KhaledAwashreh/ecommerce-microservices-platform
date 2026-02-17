package com.kawashreh.ecommerce.order_service.domain.service.impl;

import com.kawashreh.ecommerce.order_service.domain.enums.CartStatus;
import com.kawashreh.ecommerce.order_service.domain.service.CartService;
import com.kawashreh.ecommerce.order_service.dataAccess.mapper.CartItemMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.mapper.CartMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.CartItemRepository;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.CartRepository;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;
import com.kawashreh.ecommerce.order_service.domain.model.CartItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    public Cart create(Cart cart) {
        var entity = CartMapper.toEntity(cart);
        var saved = cartRepository.save(entity);
        return CartMapper.toDomain(saved);
    }

    @Override
    public Cart findById(UUID id) {
        return cartRepository.findById(id)
                .map(CartMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Cart findByUserId(UUID userId) {
        return cartRepository.findByUserId(userId)
                .map(CartMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Cart findBySessionId(UUID sessionId) {
        return cartRepository.findBySessionId(sessionId)
                .map(CartMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Cart findByUserIdAndStatus(UUID userId, CartStatus status) {
        return cartRepository.findByUserIdAndStatus(userId, status)
                .map(CartMapper::toDomain)
                .orElse(null);
    }

    @Override
    public Cart findBySessionIdAndStatus(UUID sessionId, CartStatus status) {
        return cartRepository.findBySessionIdAndStatus(sessionId, status)
                .map(CartMapper::toDomain)
                .orElse(null);
    }

    @Override
    public List<Cart> findByStatus(CartStatus status) {
        return cartRepository.findByStatus(status)
                .stream()
                .map(CartMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Cart addItem(UUID cartId, CartItem item) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        var itemEntity = CartItemMapper.toEntity(item);
        itemEntity.setCart(cartEntity);
        cartItemRepository.save(itemEntity);

        cartEntity.getCartItems().add(itemEntity);
        cartRepository.save(cartEntity);
        return CartMapper.toDomain(cartEntity);
    }

    @Override
    @Transactional
    public Cart removeItem(UUID cartId, UUID itemId) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        var itemEntity = cartItemRepository.findByIdAndCartId(itemId, cartId);
        if (itemEntity.isPresent()) {
            cartItemRepository.delete(itemEntity.get());
            cartEntity.getCartItems().remove(itemEntity.get());
            cartRepository.save(cartEntity);
        }

        return CartMapper.toDomain(cartEntity);
    }

    @Override
    @Transactional
    public Cart updateItem(UUID cartId, CartItem item) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        var itemEntity = cartItemRepository.findByIdAndCartId(item.getId(), cartId);
        if (itemEntity.isPresent()) {
            var entity = itemEntity.get();
            entity.setQuantity(item.getQuantity());
            entity.setLineTotal(item.getLineTotal());
            cartItemRepository.save(entity);
        }

        return CartMapper.toDomain(cartEntity);
    }

    @Override
    @Transactional
    public Cart clearCart(UUID cartId) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        cartItemRepository.deleteByCartId(cartId);
        cartEntity.getCartItems().clear();
        cartEntity.setSubtotal(BigDecimal.ZERO);
        cartEntity.setDiscountTotal(BigDecimal.ZERO);
        cartEntity.setTaxTotal(BigDecimal.ZERO);
        cartEntity.setShippingTotal(BigDecimal.ZERO);
        cartEntity.setTotalPrice(BigDecimal.ZERO);

        var saved = cartRepository.save(cartEntity);
        return CartMapper.toDomain(saved);
    }

    @Override
    public Cart update(Cart cart) {
        var entity = CartMapper.toEntity(cart);
        var updated = cartRepository.save(entity);
        return CartMapper.toDomain(updated);
    }

    @Override
    public void delete(UUID id) {
        cartRepository.deleteById(id);
    }

    @Override
    public Cart recalculateTotals(UUID cartId) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        BigDecimal subtotal = cartEntity.getCartItems().stream()
                .map(CartItemMapper::toDomain)
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cartEntity.setSubtotal(subtotal);


        var saved = cartRepository.save(cartEntity);
        return CartMapper.toDomain(saved);
    }
}
