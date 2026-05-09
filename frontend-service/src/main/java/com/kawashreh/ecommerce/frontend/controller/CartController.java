package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.UUID;

@Controller
public class CartController {

    @Autowired private SessionManager sessionManager;

    @GetMapping("/cart")
    public String cart(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Shopping Cart");
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        model.addAttribute("cartItems", Collections.emptyList());
        return "cart/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam UUID productId,
                            @RequestParam(defaultValue = "1") int quantity,
                            HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam UUID cartItemId, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
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
}