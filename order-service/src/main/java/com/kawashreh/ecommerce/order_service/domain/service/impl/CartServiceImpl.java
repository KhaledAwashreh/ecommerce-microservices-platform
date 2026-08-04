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
import java.util.ArrayList;
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
    public Cart getOrCreateActiveCart(UUID userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .map(CartMapper::toDomain)
                .orElseGet(() -> createActiveCart(userId));
    }

    private Cart createActiveCart(UUID userId) {
        var cart = Cart.builder()
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .cartItems(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .discountTotal(BigDecimal.ZERO)
                .taxTotal(BigDecimal.ZERO)
                .shippingTotal(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .build();
        var saved = cartRepository.save(CartMapper.toEntity(cart));
        return CartMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public Cart addItem(UUID cartId, CartItem item) {
        var cartEntity = cartRepository.findById(cartId).orElse(null);
        if (cartEntity == null) return null;

        var itemEntity = CartItemMapper.toEntity(item);
        // CartItemEntity.id is @GeneratedValue(strategy = GenerationType.UUID) - the server
        // assigns it - but CartItemDto.id is @NonNull, so every real HTTP client is forced
        // to send some id, which CartItemMapper.toEntity carries straight onto the new
        // entity. A non-null id on a "new" entity makes Spring Data's isNew() check treat it
        // as already existing, so save() calls merge() instead of persist() - Hibernate then
        // tries to update a row that was never inserted and throws
        // ObjectOptimisticLockingFailureException. Same root cause, and same fix, as
        // OrderServiceImpl.create(); found live via a smoke test where adding any item to a
        // cart failed 100% of the time.
        itemEntity.setId(null);
        itemEntity.setCart(cartEntity);
        var savedItem = cartItemRepository.save(itemEntity);

        cartEntity.getCartItems().add(savedItem);
        applyTotals(cartEntity);
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
            applyTotals(cartEntity);
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

        applyTotals(cartEntity);
        var saved = cartRepository.save(cartEntity);
        return CartMapper.toDomain(saved);
    }

    /**
     * Recomputes {@code subtotal} from the cart's line items and derives {@code totalPrice}
     * from it. {@code totalPrice} was previously never assigned anywhere in this module, so
     * every cart reported a payable total of 0.00 no matter what it contained - found live
     * via a smoke test where a cart holding 3 x 9.99 correctly showed subtotal 29.97 but
     * still reported totalPrice 0.00.
     * <p>
     * Callers must invoke this after ANY mutation of the cart's items. Previously only the
     * quantity-change endpoint did, so adding or removing an item left the stored totals
     * stale (a freshly-filled cart showed 0.00 until an unrelated quantity edit happened to
     * refresh it).
     */
    private void applyTotals(com.kawashreh.ecommerce.order_service.dataAccess.entity.CartEntity cartEntity) {
        BigDecimal subtotal = cartEntity.getCartItems().stream()
                .map(CartItemMapper::toDomain)
                .map(CartItem::getLineTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cartEntity.setSubtotal(subtotal);
        cartEntity.setTotalPrice(
                subtotal.subtract(orZero(cartEntity.getDiscountTotal()))
                        .add(orZero(cartEntity.getTaxTotal()))
                        .add(orZero(cartEntity.getShippingTotal())));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
