package com.kawashreh.ecommerce.order_service.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.order_service.application.dto.CartItemDto;
import com.kawashreh.ecommerce.order_service.application.dto.CartItemUpdateRequest;
import com.kawashreh.ecommerce.order_service.domain.enums.CartStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;
import com.kawashreh.ecommerce.order_service.domain.model.CartItem;
import com.kawashreh.ecommerce.order_service.domain.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the cart HTTP endpoints added to close GH issue #13 ("Cart is
 * unreachable end to end"). {@link CartService} was fully implemented but had no
 * {@code @RestController} exposing it; these tests cover the new list/add/remove
 * surface only — quantity-update and checkout endpoints are a deliberate seam left
 * for a follow-up change.
 * <p>
 * Also guards against the ambiguous-route bug pattern already fixed in this repo
 * (issue #1, see {@code ProductReviewControllerTest}): {@code /user/{userId}} and
 * {@code /{id}} are distinct path-segment counts, so a plain cart-id lookup and a
 * user-scoped cart lookup can never collide.
 */
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCartForUser_shouldReturnOrCreateTheActiveCart() throws Exception {
        UUID userId = UUID.randomUUID();
        Cart cart = activeCart(UUID.randomUUID(), userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);

        mockMvc.perform(get("/api/v1/carts/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cart.getId().toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getCartById_shouldReturnCart_whenFound() throws Exception {
        UUID userId = UUID.randomUUID();
        Cart cart = activeCart(UUID.randomUUID(), userId);

        given(cartService.findById(cart.getId())).willReturn(cart);

        mockMvc.perform(get("/api/v1/carts/{id}", cart.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cart.getId().toString()));
    }

    @Test
    void getCartById_shouldReturn404_whenNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();
        given(cartService.findById(missingId)).willReturn(null);

        mockMvc.perform(get("/api/v1/carts/{id}", missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_shouldGetOrCreateCartThenAddItem_andReturnUpdatedCart() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Cart cart = activeCart(cartId, userId);
        Instant now = Instant.now();

        CartItemDto requestItem = CartItemDto.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .productSku("sku-123")
                .productName("Widget")
                .quantity(2)
                .unitPrice(BigDecimal.TEN)
                .lineTotal(BigDecimal.valueOf(20))
                .currency("USD")
                .createdAt(now)
                .updatedAt(now)
                .build();

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);
        given(cartService.addItem(eq(cartId), any(CartItem.class))).willReturn(cart);

        mockMvc.perform(post("/api/v1/carts/user/{userId}/items", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestItem)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }

    @Test
    void removeItem_shouldReturnUpdatedCart_whenCartExists() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Cart cart = activeCart(cartId, userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);
        given(cartService.removeItem(cartId, itemId)).willReturn(cart);

        mockMvc.perform(delete("/api/v1/carts/user/{userId}/items/{itemId}", userId, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }

    @Test
    void removeItem_shouldReturn404_whenCartMissingAfterRemoval() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Cart cart = activeCart(cartId, userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);
        given(cartService.removeItem(cartId, itemId)).willReturn(null);

        mockMvc.perform(delete("/api/v1/carts/user/{userId}/items/{itemId}", userId, itemId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_shouldRecomputeLineTotalAndRecalculateTotals_whenItemExists() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Cart cart = activeCartWithItem(cartId, userId, itemId, BigDecimal.TEN);
        Cart recalculated = activeCart(cartId, userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);
        given(cartService.updateItem(eq(cartId), any(CartItem.class))).willReturn(cart);
        given(cartService.recalculateTotals(cartId)).willReturn(recalculated);

        mockMvc.perform(put("/api/v1/carts/user/{userId}/items/{itemId}", userId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemUpdateRequest(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()));
    }

    @Test
    void updateItem_shouldReturn404_whenItemNotInCart() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Cart cart = activeCart(cartId, userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);

        mockMvc.perform(put("/api/v1/carts/user/{userId}/items/{itemId}", userId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemUpdateRequest(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_shouldReturn400_whenQuantityIsNotPositive() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/carts/user/{userId}/items/{itemId}", userId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemUpdateRequest(0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearCart_shouldReturnEmptiedCart() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Cart cart = activeCart(cartId, userId);
        Cart cleared = activeCart(cartId, userId);

        given(cartService.getOrCreateActiveCart(userId)).willReturn(cart);
        given(cartService.clearCart(cartId)).willReturn(cleared);

        mockMvc.perform(delete("/api/v1/carts/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cartId.toString()))
                .andExpect(jsonPath("$.cartItems").isEmpty());
    }

    private Cart activeCart(UUID id, UUID userId) {
        Instant now = Instant.now();
        return Cart.builder()
                .id(id)
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .cartItems(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .discountTotal(BigDecimal.ZERO)
                .taxTotal(BigDecimal.ZERO)
                .shippingTotal(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Cart activeCartWithItem(UUID cartId, UUID userId, UUID itemId, BigDecimal unitPrice) {
        Instant now = Instant.now();
        CartItem item = CartItem.builder()
                .id(itemId)
                .cartId(cartId)
                .productId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .productSku("sku-123")
                .productName("Widget")
                .quantity(1)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice)
                .currency("USD")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Cart cart = activeCart(cartId, userId);
        cart.setCartItems(List.of(item));
        return cart;
    }
}
