package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.CartServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.CartDto;
import com.kawashreh.ecommerce.frontend.dto.CartItemDto;
import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
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

    public CartController(SessionManager sessionManager,
                           CartServiceClient cartServiceClient,
                           ProfileFacade profileFacade,
                           ProductFacade productFacade) {
        this.sessionManager = sessionManager;
        this.cartServiceClient = cartServiceClient;
        this.profileFacade = profileFacade;
        this.productFacade = productFacade;
    }

    @GetMapping("/cart")
    public String cart(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Shopping Cart");
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

    @GetMapping("/checkout")
    public String checkout(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Checkout");
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        return "cart/checkout";
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
