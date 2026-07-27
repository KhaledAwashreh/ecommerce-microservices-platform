package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.CartServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.CartDto;
import com.kawashreh.ecommerce.frontend.dto.CartItemDto;
import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.request.CartItemUpdateRequest;
import com.kawashreh.ecommerce.frontend.facade.OrderFacade;
import com.kawashreh.ecommerce.frontend.facade.ProductFacade;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for GH #6: /checkout/place and /cart/update previously had no
 * handler at all in CartController, so checkout could never complete and the cart
 * quantity <select> silently 404d.
 * <p>
 * Plain Mockito unit tests with no Spring context - mirrors the style already used by
 * OrderControllerTest in this module. Not executed against Docker/Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private CartServiceClient cartServiceClient;

    @Mock
    private ProfileFacade profileFacade;

    @Mock
    private ProductFacade productFacade;

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CartController cartController;

    private void authenticateAs(String username, UUID userId) {
        when(sessionManager.isAuthenticated(request)).thenReturn(true);
        when(sessionManager.getUsername(request)).thenReturn(username);
        when(profileFacade.getUserByUsername(username))
                .thenReturn(UserDto.builder().id(userId).username(username).build());
    }

    // ---- POST /cart/update ----

    @Test
    void updateCartItem_redirectsToLogin_whenUnauthenticated() {
        when(sessionManager.isAuthenticated(request)).thenReturn(false);

        String view = cartController.updateCartItem(UUID.randomUUID(), 3, request);

        assertEquals("redirect:/login", view);
        verify(cartServiceClient, never()).updateItem(any(), any(), any());
    }

    @Test
    void updateCartItem_callsCartServiceClientWithNewQuantity_andRedirectsToCart() {
        UUID userId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        authenticateAs("alice", userId);

        String view = cartController.updateCartItem(itemId, 4, request);

        assertEquals("redirect:/cart", view);
        ArgumentCaptor<CartItemUpdateRequest> captor = ArgumentCaptor.forClass(CartItemUpdateRequest.class);
        verify(cartServiceClient).updateItem(eq(userId), eq(itemId), captor.capture());
        assertEquals(4, captor.getValue().getQuantity());
    }

    // ---- POST /checkout/place ----

    @Test
    void placeOrder_redirectsToLogin_whenUnauthenticated() {
        when(sessionManager.isAuthenticated(request)).thenReturn(false);

        String view = cartController.placeOrder("1", "CREDIT_CARD", request);

        assertEquals("redirect:/login", view);
        verify(orderFacade, never()).createOrder(any());
    }

    @Test
    void placeOrder_redirectsWithError_whenCartIsEmpty() {
        UUID userId = UUID.randomUUID();
        authenticateAs("alice", userId);
        when(cartServiceClient.getCartForUser(userId))
                .thenReturn(CartDto.builder().id(UUID.randomUUID()).cartItems(Collections.emptyList()).build());

        String view = cartController.placeOrder("1", "CREDIT_CARD", request);

        assertTrue(view.startsWith("redirect:/checkout?error="));
        verify(orderFacade, never()).createOrder(any());
    }

    @Test
    void placeOrder_createsOrderFromCartItems_clearsCart_andRedirectsToOrderDetail() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticateAs("alice", userId);

        CartItemDto item = CartItemDto.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .storeId(storeId)
                .productSku(productId.toString())
                .productName("Widget")
                .quantity(2)
                .unitPrice(BigDecimal.TEN)
                .lineTotal(BigDecimal.valueOf(20))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        CartDto cart = CartDto.builder().id(UUID.randomUUID()).cartItems(List.of(item)).build();
        when(cartServiceClient.getCartForUser(userId)).thenReturn(cart);

        OrderDto createdOrder = OrderDto.builder().id(orderId).buyer(userId).build();
        when(orderFacade.createOrder(any(OrderDto.class))).thenReturn(createdOrder);

        String view = cartController.placeOrder("1", "CREDIT_CARD", request);

        assertEquals("redirect:/orders/" + orderId, view);

        ArgumentCaptor<OrderDto> orderCaptor = ArgumentCaptor.forClass(OrderDto.class);
        verify(orderFacade).createOrder(orderCaptor.capture());
        OrderDto submitted = orderCaptor.getValue();
        assertEquals(userId, submitted.getBuyer());
        assertEquals(storeId, submitted.getStoreId());
        assertEquals(1, submitted.getSelectedItems().size());
        assertEquals(productId, submitted.getSelectedItems().get(0).getProductSku());
        assertEquals(2, submitted.getSelectedItems().get(0).getQuantity());

        verify(cartServiceClient, times(1)).clearCart(userId);
    }

    @Test
    void placeOrder_redirectsWithError_whenOrderCreationFails() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        authenticateAs("alice", userId);

        CartItemDto item = CartItemDto.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .storeId(UUID.randomUUID())
                .productSku(productId.toString())
                .productName("Widget")
                .quantity(1)
                .unitPrice(BigDecimal.ZERO)
                .lineTotal(BigDecimal.ZERO)
                .currency("USD")
                .build();
        CartDto cart = CartDto.builder().id(UUID.randomUUID()).cartItems(List.of(item)).build();
        when(cartServiceClient.getCartForUser(userId)).thenReturn(cart);
        when(orderFacade.createOrder(any(OrderDto.class))).thenReturn(null);

        String view = cartController.placeOrder("1", "CREDIT_CARD", request);

        assertTrue(view.startsWith("redirect:/checkout?error="));
        verify(cartServiceClient, never()).clearCart(any());
    }

    @Test
    void placeOrder_redirectsWithError_whenCartItemHasUnparseableProductSku() {
        UUID userId = UUID.randomUUID();
        authenticateAs("alice", userId);

        CartItemDto badItem = CartItemDto.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .productSku("not-a-uuid")
                .productName("Widget")
                .quantity(1)
                .unitPrice(BigDecimal.ZERO)
                .lineTotal(BigDecimal.ZERO)
                .currency("USD")
                .build();
        CartDto cart = CartDto.builder().id(UUID.randomUUID()).cartItems(List.of(badItem)).build();
        when(cartServiceClient.getCartForUser(userId)).thenReturn(cart);

        String view = cartController.placeOrder("1", "CREDIT_CARD", request);

        assertTrue(view.startsWith("redirect:/checkout?error="));
        verify(orderFacade, never()).createOrder(any());
    }
}
