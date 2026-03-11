package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.JwtService;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.*;
import com.kawashreh.ecommerce.frontend.service.ApiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class FrontendController {

    @Autowired
    private ApiService apiService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private JwtService jwtService;

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Home - E-Commerce");
        return "redirect:/products";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("title", "Login");
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, 
                        @RequestParam String password,
                        HttpServletRequest request,
                        Model model) {
        UserLoginDto loginDto = new UserLoginDto(username, password);
        
        try {
            String token = apiService.login(loginDto).block();
            if (token != null && !token.isEmpty()) {
                sessionManager.storeToken(request, token, username);
                return "redirect:/products";
            }
        } catch (Exception e) {
            // Login failed
        }
        return "redirect:/login?error=true";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("title", "Create Account");
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                          @RequestParam String username,
                          @RequestParam String email,
                          @RequestParam String phone,
                          @RequestParam String birthdate,
                          @RequestParam String gender,
                          @RequestParam String rawPassword,
                          HttpServletRequest request,
                          Model model) {
        UserRegisterDto registerDto = UserRegisterDto.builder()
                .name(name)
                .username(username)
                .email(email)
                .phone(phone)
                .rawPassword(rawPassword)
                .gender(gender)
                .build();
        
        try {
            UserDto user = apiService.register(registerDto).block();
            if (user != null) {
                return "redirect:/login?registered=true";
            }
        } catch (Exception e) {
            // Registration failed
        }
        return "redirect:/register?error=true";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        sessionManager.invalidate(request);
        return "redirect:/login";
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) String category,
                          @RequestParam(required = false) String q,
                          Model model,
                          HttpServletRequest request) {
        model.addAttribute("title", "All Products");
        
        String token = sessionManager.getToken(request);
        if (token == null) {
            model.addAttribute("products", Collections.emptyList());
            return "product/list";
        }

        try {
            List<ProductDto> products = apiService.getAllProducts(token).block();
            model.addAttribute("products", products != null ? products : Collections.emptyList());
        } catch (Exception e) {
            model.addAttribute("products", Collections.emptyList());
        }
        
        return "product/list";
    }

    @GetMapping("/products/{productId}")
    public String productDetail(@PathVariable String productId,
                               Model model,
                               HttpServletRequest request) {
        String token = sessionManager.getToken(request);
        
        try {
            ProductDto product = apiService.getProductById(productId, token).block();
            model.addAttribute("product", product);
        } catch (Exception e) {
            model.addAttribute("error", "Product not found");
        }
        
        return "product/detail";
    }

    @GetMapping("/cart")
    public String cart(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Shopping Cart");
        
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }

        model.addAttribute("cartItems", Collections.emptyList());
        model.addAttribute("subtotal", "0.00");
        model.addAttribute("tax", "0.00");
        model.addAttribute("total", "0.00");
        
        return "cart/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam String productId,
                           @RequestParam(defaultValue = "1") int quantity,
                           HttpServletRequest request) {
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }
        return "redirect:/cart";
    }

    @GetMapping("/orders")
    public String orders(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Orders");
        
        String token = sessionManager.getToken(request);
        String username = sessionManager.getUsername(request);
        
        if (token == null || username == null) {
            return "redirect:/login";
        }

        try {
            UserDto user = apiService.getUserByUsername(username, token).block();
            if (user != null) {
                List<OrderDto> orders = apiService.getOrdersByBuyer(user.getId().toString(), token).block();
                model.addAttribute("orders", orders != null ? orders : Collections.emptyList());
            }
        } catch (Exception e) {
            model.addAttribute("orders", Collections.emptyList());
        }
        
        return "order/orders";
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Account");
        
        String token = sessionManager.getToken(request);
        String username = sessionManager.getUsername(request);
        
        if (token == null || username == null) {
            return "redirect:/login";
        }

        try {
            UserDto user = apiService.getUserByUsername(username, token).block();
            model.addAttribute("user", user);
            
            List<AddressDto> addresses = apiService.getAddresses(token).block();
            model.addAttribute("addresses", addresses != null ? addresses : Collections.emptyList());
        } catch (Exception e) {
            // Handle error
        }
        
        return "user/profile";
    }

    @GetMapping("/addresses")
    public String addresses(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Addresses");
        
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }

        try {
            List<AddressDto> addresses = apiService.getAddresses(token).block();
            model.addAttribute("addresses", addresses != null ? addresses : Collections.emptyList());
        } catch (Exception e) {
            model.addAttribute("addresses", Collections.emptyList());
        }
        
        return "user/addresses";
    }

    @PostMapping("/addresses/add")
    public String addAddress(@RequestParam String street,
                             @RequestParam String city,
                             @RequestParam String state,
                             @RequestParam String postalCode,
                             @RequestParam String country,
                             @RequestParam String phoneNumber,
                             @RequestParam(required = false) String additionalInformation,
                             @RequestParam(required = false, defaultValue = "false") boolean defaultAddress,
                             HttpServletRequest request) {
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }

        AddressDto address = AddressDto.builder()
                .street(street)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .country(country)
                .phoneNumber(phoneNumber)
                .additionalInformation(additionalInformation)
                .defaultAddress(defaultAddress)
                .build();

        try {
            apiService.createAddress(address, token).block();
        } catch (Exception e) {
            // Handle error
        }
        
        return "redirect:/addresses";
    }

    @PostMapping("/addresses/delete")
    public String deleteAddress(@RequestParam String addressId,
                                HttpServletRequest request) {
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }

        try {
            apiService.deleteAddress(addressId, token).block();
        } catch (Exception e) {
            // Handle error
        }
        
        return "redirect:/addresses";
    }

    @GetMapping("/inventory")
    public String inventory(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Inventory Management");
        
        String token = sessionManager.getToken(request);
        if (token == null) {
            return "redirect:/login";
        }

        model.addAttribute("inventory", Collections.emptyList());
        
        return "inventory/inventory";
    }
}
