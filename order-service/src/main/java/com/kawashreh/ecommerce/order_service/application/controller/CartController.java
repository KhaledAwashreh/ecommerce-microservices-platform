package com.kawashreh.ecommerce.order_service.application.controller;

import com.kawashreh.ecommerce.order_service.application.dto.CartDto;
import com.kawashreh.ecommerce.order_service.application.dto.CartItemDto;
import com.kawashreh.ecommerce.order_service.application.dto.CartItemUpdateRequest;
import com.kawashreh.ecommerce.order_service.application.mapper.CartHttpMapper;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;
import com.kawashreh.ecommerce.order_service.domain.model.CartItem;
import com.kawashreh.ecommerce.order_service.domain.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exposes {@link CartService} over HTTP. The calling user is identified by an
 * explicit {@code userId} path variable, resolved upstream (frontend-service
 * resolves it via its session + user-service lookup) — this module does not
 * itself read any identity header, matching {@link OrderController}, which
 * takes {@code buyerId}/{@code sellerId} as explicit path variables rather
 * than trusting a gateway-propagated header.
 * <p>
 * List/add/remove were added for GH #13. {@code updateItem} (quantity change) and
 * {@code clearCart} (post-checkout) are wired here for GH #6, closing the seam that
 * #13 deliberately left open. Cart-to-order checkout itself is orchestrated by
 * {@code OrderController}/{@code OrderService.create} in frontend-service, not here.
 */
@RestController
@RequestMapping(ApiPaths.CART_BASE)
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping(ApiPaths.CART_BY_USER)
    public ResponseEntity<CartDto> getCartForUser(@PathVariable UUID userId) {
        var cart = cartService.getOrCreateActiveCart(userId);
        return ResponseEntity.ok(CartHttpMapper.toDto(cart));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CartDto> getCartById(@PathVariable UUID id) {
        var cart = cartService.findById(id);
        if (cart == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CartHttpMapper.toDto(cart));
    }

    @PostMapping(ApiPaths.CART_ITEMS_BY_USER)
    public ResponseEntity<CartDto> addItem(@PathVariable UUID userId, @RequestBody CartItemDto itemDto) {
        var cart = cartService.getOrCreateActiveCart(userId);
        var updated = cartService.addItem(cart.getId(), CartHttpMapper.toDomain(itemDto));
        return ResponseEntity.status(HttpStatus.CREATED).body(CartHttpMapper.toDto(updated));
    }

    @DeleteMapping(ApiPaths.CART_ITEM_BY_USER)
    public ResponseEntity<CartDto> removeItem(@PathVariable UUID userId, @PathVariable UUID itemId) {
        var cart = cartService.getOrCreateActiveCart(userId);
        var updated = cartService.removeItem(cart.getId(), itemId);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CartHttpMapper.toDto(updated));
    }

    /**
     * Quantity-change endpoint — the seam GH #13 left open. {@link CartService#updateItem}
     * only overwrites {@code quantity}/{@code lineTotal} on the targeted row, so this method
     * recomputes {@code lineTotal} from the item's existing {@code unitPrice} before calling
     * it, then recalculates the cart's totals so the response reflects the change.
     */
    @PutMapping(ApiPaths.CART_ITEM_BY_USER)
    public ResponseEntity<CartDto> updateItem(@PathVariable UUID userId,
                                               @PathVariable UUID itemId,
                                               @RequestBody CartItemUpdateRequest request) {
        if (request == null || request.getQuantity() < 1) {
            return ResponseEntity.badRequest().build();
        }

        Cart cart = cartService.getOrCreateActiveCart(userId);
        CartItem existing = findItem(cart, itemId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        BigDecimal unitPrice = existing.getUnitPrice() != null ? existing.getUnitPrice() : BigDecimal.ZERO;
        CartItem update = CartItem.builder()
                .id(itemId)
                .quantity(request.getQuantity())
                .lineTotal(unitPrice.multiply(BigDecimal.valueOf(request.getQuantity())))
                .build();

        cartService.updateItem(cart.getId(), update);
        var recalculated = cartService.recalculateTotals(cart.getId());
        return ResponseEntity.ok(CartHttpMapper.toDto(recalculated));
    }

    /**
     * Empties the caller's active cart. Called by frontend-service right after a
     * checkout successfully creates an order, so the same cart cannot be checked out
     * twice. Reuses {@link CartService#clearCart} — items are removed and totals reset,
     * but the cart itself stays {@code ACTIVE} rather than transitioning to
     * {@code CONVERTED} (no code path in this module ever performs that transition today).
     */
    @DeleteMapping(ApiPaths.CART_BY_USER)
    public ResponseEntity<CartDto> clearCart(@PathVariable UUID userId) {
        Cart cart = cartService.getOrCreateActiveCart(userId);
        Cart cleared = cartService.clearCart(cart.getId());
        return ResponseEntity.ok(CartHttpMapper.toDto(cleared));
    }

    private CartItem findItem(Cart cart, UUID itemId) {
        if (cart.getCartItems() == null) {
            return null;
        }
        return cart.getCartItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }
}
