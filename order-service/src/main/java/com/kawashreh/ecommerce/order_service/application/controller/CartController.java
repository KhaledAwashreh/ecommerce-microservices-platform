package com.kawashreh.ecommerce.order_service.application.controller;

import com.kawashreh.ecommerce.order_service.application.dto.CartDto;
import com.kawashreh.ecommerce.order_service.application.dto.CartItemDto;
import com.kawashreh.ecommerce.order_service.application.mapper.CartHttpMapper;
import com.kawashreh.ecommerce.order_service.constants.ApiPaths;
import com.kawashreh.ecommerce.order_service.domain.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Exposes {@link CartService} over HTTP. The calling user is identified by an
 * explicit {@code userId} path variable, resolved upstream (frontend-service
 * resolves it via its session + user-service lookup) — this module does not
 * itself read any identity header, matching {@link OrderController}, which
 * takes {@code buyerId}/{@code sellerId} as explicit path variables rather
 * than trusting a gateway-propagated header.
 * <p>
 * Only list/add/remove are exposed here. Quantity updates
 * ({@code PUT /user/{userId}/items/{itemId}}) and cart-to-order checkout are
 * a deliberate seam left for a follow-up change — {@link CartService} already
 * supports {@code updateItem}/{@code clearCart}/{@code recalculateTotals},
 * they are simply not wired to an endpoint yet.
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
}
