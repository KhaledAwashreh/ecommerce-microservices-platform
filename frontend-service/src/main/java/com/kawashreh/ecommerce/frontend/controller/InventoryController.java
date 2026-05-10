package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.InventoryDto;
import com.kawashreh.ecommerce.frontend.client.InventoryServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.UUID;

@Controller
public class InventoryController {

    @Autowired private SessionManager sessionManager;
    @Autowired private InventoryServiceClient inventoryServiceClient;

    @GetMapping("/inventory")
    public String inventory(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Inventory Management");
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        // TODO: Implement actual inventory list - currently stubbed
        model.addAttribute("inventory", Collections.emptyList());
        return "inventory/inventory";
    }

    @GetMapping("/inventory/{productVariationId}")
    public String inventoryDetail(@PathVariable UUID productVariationId, Model model, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        try {
            InventoryDto inventory = inventoryServiceClient.getInventoryByVariation(productVariationId);
            model.addAttribute("inventory", inventory);
        } catch (Exception e) {
            model.addAttribute("error", "Inventory not found");
        }
        return "inventory/detail";
    }
}