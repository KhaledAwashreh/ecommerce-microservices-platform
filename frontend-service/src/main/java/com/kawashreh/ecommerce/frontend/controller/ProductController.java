package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import com.kawashreh.ecommerce.frontend.dto.facade.ProductWithDetailsDto;
import com.kawashreh.ecommerce.frontend.facade.ProductFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired private SessionManager sessionManager;
    @Autowired private ProductFacade productFacade;

    @GetMapping("/products")
    public String products(Model model, HttpServletRequest request) {
        model.addAttribute("title", "All Products");
        if (!sessionManager.isAuthenticated(request)) {
            model.addAttribute("products", Collections.emptyList());
            return "product/list";
        }
        List<ProductDto> products = productFacade.getAllProducts();
        model.addAttribute("products", products != null ? products : Collections.emptyList());
        return "product/list";
    }

    @GetMapping("/products/{productId}")
    public String productDetail(@PathVariable UUID productId, Model model) {
        ProductWithDetailsDto productWithDetails = productFacade.getProductWithDetails(productId);
        if (productWithDetails.getProduct() == null) {
            model.addAttribute("error", "Product not found");
        }
        model.addAttribute("product", productWithDetails.getProduct());
        model.addAttribute("category", productWithDetails.getCategory());
        return "product/detail";
    }

    @GetMapping("/products/search")
    public String searchProducts(@RequestParam(required = false) String q, Model model, HttpServletRequest request) {
        model.addAttribute("title", "Search Results");
        if (q == null || q.isBlank()) {
            model.addAttribute("products", Collections.emptyList());
            return "product/list";
        }
        List<ProductDto> products = productFacade.searchProducts(q);
        model.addAttribute("products", products);
        return "product/list";
    }
}