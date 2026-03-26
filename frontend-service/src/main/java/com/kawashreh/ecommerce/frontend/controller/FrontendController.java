package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.*;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.*;
import com.kawashreh.ecommerce.frontend.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@Controller
public class FrontendController {

    private static final Logger log = LoggerFactory.getLogger(FrontendController.class);

    @Autowired private SessionManager sessionManager;
    @Autowired private UserServiceClient userServiceClient;
    @Autowired private AddressServiceClient addressServiceClient;
    @Autowired private ProductServiceClient productServiceClient;
    @Autowired private OrderServiceClient orderServiceClient;
    @Autowired private InventoryServiceClient inventoryServiceClient;

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @GetMapping("/")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("title", "Login");
        if (error != null) model.addAttribute("error", "Invalid username or password");
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute UserLoginRequest loginRequest, HttpServletRequest request) {
        try {
            String token = userServiceClient.login(loginRequest);
            if (token != null && !token.isEmpty()) {
                sessionManager.storeToken(request, token, loginRequest.getUsername());
                return "redirect:/products";
            }
        } catch (Exception e) {
            log.warn("Login failed for '{}': {}", loginRequest.getUsername(), e.getMessage());
        }
        return "redirect:/login?error=true";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("title", "Create Account");
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestBody UserRegisterRequest registerRequest) {
        if (registerRequest.getBirthdate() == null) return "redirect:/register?error=true";
        try {
            if (userServiceClient.register(registerRequest) != null)
                return "redirect:/login?registered=true";
        } catch (Exception e) {
            log.warn("Registration failed: {}", e.getMessage());
        }
        return "redirect:/register?error=true";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        sessionManager.invalidate(request);
        return "redirect:/login";
    }

    // -------------------------------------------------------------------------
    // Products
    // -------------------------------------------------------------------------

    @GetMapping("/products")
    public String products(Model model, HttpServletRequest request) {
        model.addAttribute("title", "All Products");
        if (!sessionManager.isAuthenticated(request)) {
            model.addAttribute("products", Collections.emptyList());
            return "product/list";
        }
        try {
            var products = productServiceClient.getAllProducts();
            model.addAttribute("products", products != null ? products : Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to fetch products: {}", e.getMessage());
            model.addAttribute("products", Collections.emptyList());
        }
        return "product/list";
    }

    @GetMapping("/products/{productId}")
    public String productDetail(@PathVariable UUID productId, Model model) {
        try {
            model.addAttribute("product", productServiceClient.getProductById(productId));
        } catch (Exception e) {
            log.warn("Product not found: {}", productId);
            model.addAttribute("error", "Product not found");
        }
        return "product/detail";
    }

    // -------------------------------------------------------------------------
    // Cart (not ready)
    // -------------------------------------------------------------------------

//    @GetMapping("/cart")
//    public String cart(Model model, HttpServletRequest request) { ... }

//    @PostMapping("/cart/add")
//    public String addToCart(...) { ... }

    // -------------------------------------------------------------------------
    // Orders
    // -------------------------------------------------------------------------

    @GetMapping("/orders")
    public String orders(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Orders");
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) return "redirect:/login";
        try {
            UserDto user = userServiceClient.getUserByUsername(username);
            var orders = (user != null && user.getId() != null)
                    ? orderServiceClient.getOrdersByBuyer(user.getId())
                    : Collections.emptyList();
            model.addAttribute("orders", orders != null ? orders : Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to load orders for '{}': {}", username, e.getMessage());
            model.addAttribute("orders", Collections.emptyList());
        }
        return "order/orders";
    }

    // -------------------------------------------------------------------------
    // Profile & Addresses
    // -------------------------------------------------------------------------

    @GetMapping("/profile")
    public String profile(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Account");
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) return "redirect:/login";
        try {
            model.addAttribute("user", userServiceClient.getUserByUsername(username));
            var addresses = addressServiceClient.getAddresses();
            model.addAttribute("addresses", addresses != null ? addresses : Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to load profile for '{}': {}", username, e.getMessage());
        }
        return "user/profile";
    }

    @GetMapping("/addresses")
    public String addresses(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Addresses");
        if (!sessionManager.isAuthenticated(request)) return "redirect:/login";
        try {
            var addresses = addressServiceClient.getAddresses();
            model.addAttribute("addresses", addresses != null ? addresses : Collections.emptyList());
        } catch (Exception e) {
            log.error("Failed to load addresses: {}", e.getMessage());
            model.addAttribute("addresses", Collections.emptyList());
        }
        return "user/addresses";
    }

    @PostMapping("/addresses/add")
    public String addAddress(@ModelAttribute AddressRequest addressRequest, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) return "redirect:/login";
        try {
            addressServiceClient.createAddress(addressRequest);
        } catch (Exception e) {
            log.error("Failed to add address: {}", e.getMessage());
        }
        return "redirect:/addresses";
    }

    @PostMapping("/addresses/delete")
    public String deleteAddress(@RequestParam UUID addressId, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) return "redirect:/login";
        try {
            addressServiceClient.deleteAddress(addressId);
        } catch (Exception e) {
            log.error("Failed to delete address '{}': {}", addressId, e.getMessage());
        }
        return "redirect:/addresses";
    }
}