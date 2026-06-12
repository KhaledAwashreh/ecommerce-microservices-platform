package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.UserServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.request.UserLoginRequest;
import com.kawashreh.ecommerce.frontend.dto.request.UserRegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired private SessionManager sessionManager;
    @Autowired private UserServiceClient userServiceClient;

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
    public String register(@ModelAttribute UserRegisterRequest registerRequest) {
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
}
