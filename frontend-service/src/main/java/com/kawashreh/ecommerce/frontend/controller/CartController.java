package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.CartServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.CartDto;
import com.kawashreh.ecommerce.frontend.dto.CartItemDto;
import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.OrderItemDto;
import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.request.CartItemUpdateRequest;
import com.kawashreh.ecommerce.frontend.facade.OrderFacade;
import com.kawashreh.ecommerce.frontend.facade.ProductFacade;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private static final String DEFAULT_CURRENCY = "USD";

    private final SessionManager sessionManager;
    private final CartServiceClient cartServiceClient;
    private final ProfileFacade profileFacade;
    private final ProductFacade productFacade;
    private final OrderFacade orderFacade;

    public CartController(SessionManager sessionManager,
                           CartServiceClient cartServiceClient,
                           ProfileFacade profileFacade,
                           ProductFacade productFacade,
                           OrderFacade orderFacade) {
        this.sessionManager = sessionManager;
        this.cartServiceClient = cartServiceClient;
        this.profileFacade = profileFacade;
        this.productFacade = productFacade;
        this.orderFacade = orderFacade;
    }

    @GetMapping("/cart")
    public String cart(@RequestParam(required = false) String error, Model model, HttpServletRequest request) {
        model.addAttribute("title", "Shopping Cart");
        if (error != null) {
            model.addAttribute("error", error);
        }
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        UUID userId = resolveUserId(request);
        if (userId == null) {
            return "redirect:/login";
        }

        CartDto cart = fetchCart(userId);
        List<CartItemDto> items = (cart != null && cart.getCartItems() != null)
                ? cart.getCartItems()
                : Collections.emptyList();

        model.addAttribute("cartItems", items);
        model.addAttribute("subtotal", cart != null ? cart.getSubtotal() : BigDecimal.ZERO);
        model.addAttribute("tax", cart != null ? cart.getTaxTotal() : BigDecimal.ZERO);
        model.addAttribute("total", cart != null ? cart.getTotalPrice() : BigDecimal.ZERO);
        return "cart/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam UUID productId,
                             @RequestParam(defaultValue = "1") int quantity,
                             HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        UUID userId = resolveUserId(request);
        if (userId == null) {
            return "redirect:/login";
        }

        ProductDto product = productFacade.getProductWithDetails(productId).getProduct();
        if (product == null) {
            return "redirect:/cart";
        }

        CartItemDto item = CartItemDto.builder()
                .productId(product.getId())
                .storeId(product.getOwnerId())
                .productSku(product.getId().toString())
                .productName(product.getName())
                .quantity(quantity)
                .unitPrice(BigDecimal.ZERO)
                .lineTotal(BigDecimal.ZERO)
                .currency(DEFAULT_CURRENCY)
                .build();

        cartServiceClient.addItem(userId, item);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam UUID itemId, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        UUID userId = resolveUserId(request);
        if (userId == null) {
            return "redirect:/login";
        }

        cartServiceClient.removeItem(userId, itemId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCartItem(@RequestParam UUID itemId,
                                  @RequestParam int quantity,
                                  HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        UUID userId = resolveUserId(request);
        if (userId == null) {
            return "redirect:/login";
        }

        cartServiceClient.updateItem(userId, itemId, CartItemUpdateRequest.builder().quantity(quantity).build());
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam(required = false) String error, Model model, HttpServletRequest request) {
        model.addAttribute("title", "Checkout");
        if (error != null) {
            model.addAttribute("error", error);
        }
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        return "cart/checkout";
    }

    /**
     * Terminal step of cart -> order. Only creates the order via {@link OrderFacade}
     * (order-service's {@code /api/v1/orders}, the plain create path — see class notes
     * on {@code createOrderFromCart}); it does not invoke payment-service. Payment
     * orchestration is a separate follow-up (GH #9) and is deliberately left untouched
     * here so that seam stays clean.
     * <p>
     * {@code addressId}/{@code paymentMethod} are accepted because {@code checkout.html}
     * submits them, but neither is used: the order domain model has no shipping-address
     * field, and payment is out of scope for this handler.
     */
    @PostMapping("/checkout/place")
    public String placeOrder(@RequestParam(required = false) String addressId,
                              @RequestParam(required = false) String paymentMethod,
                              HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        UUID userId = resolveUserId(request);
        if (userId == null) {
            return "redirect:/login";
        }

        try {
            CartDto cart = fetchCart(userId);
            if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
                return "redirect:/checkout?error=" + encode("Your cart is empty");
            }

            OrderDto orderToCreate;
            try {
                orderToCreate = buildOrderFromCart(cart, userId);
            } catch (Exception e) {
                log.error("Failed to build order from cart {} for user {}: {}", cart.getId(), userId, e.getMessage());
                return "redirect:/checkout?error=" + encode("Unable to process items in your cart");
            }

            OrderDto created = orderFacade.createOrder(orderToCreate);
            if (created == null || created.getId() == null) {
                return "redirect:/checkout?error=" + encode("Failed to place your order. Please try again.");
            }

            try {
                cartServiceClient.clearCart(userId);
            } catch (Exception e) {
                log.warn("Order {} placed but failed to clear cart for user {}: {}", created.getId(), userId, e.getMessage());
            }

            return "redirect:/orders/" + created.getId();
        } catch (Exception e) {
            log.error("Unexpected error placing order for user {}: {}", userId, e.getMessage(), e);
            return "redirect:/checkout?error=" + encode("Failed to place your order. Please try again.");
        }
    }

    /**
     * Maps the cart's items onto an {@link OrderDto} for the order-service create
     * endpoint. order-service's own {@code OrderDto}/{@code OrderItemDto} mark several
     * fields {@code @NonNull} (including {@code id}), so every field the wire contract
     * requires is populated here rather than left null.
     * <p>
     * Known limitation: {@code Order} has a single {@code storeId}/{@code seller}, so a
     * cart with items from more than one store collapses onto the first item's store —
     * this platform has no multi-seller order splitting today. Not a regression from
     * this change; out of scope for GH #6.
     */
    private OrderDto buildOrderFromCart(CartDto cart, UUID userId) {
        Instant now = Instant.now();
        List<OrderItemDto> items = new ArrayList<>();
        for (CartItemDto item : cart.getCartItems()) {
            UUID productSku = UUID.fromString(item.getProductSku());
            items.add(OrderItemDto.builder()
                    .id(UUID.randomUUID())
                    .productSku(productSku)
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        UUID storeId = cart.getCartItems().get(0).getStoreId();

        return OrderDto.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .seller(storeId)
                .buyer(userId)
                .status("PENDING")
                .selectedItems(items)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private UUID resolveUserId(HttpServletRequest request) {
        String username = sessionManager.getUsername(request);
        if (username == null) {
            return null;
        }
        UserDto user = profileFacade.getUserByUsername(username);
        return user != null ? user.getId() : null;
    }

    private CartDto fetchCart(UUID userId) {
        try {
            return cartServiceClient.getCartForUser(userId);
        } catch (Exception e) {
            log.error("Failed to fetch cart for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
