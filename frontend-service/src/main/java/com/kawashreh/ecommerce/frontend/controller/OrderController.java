package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.OrderDto;
import com.kawashreh.ecommerce.frontend.dto.facade.OrderWithDetailsDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.facade.OrderFacade;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired private SessionManager sessionManager;
    @Autowired private OrderFacade orderFacade;
    @Autowired private ProfileFacade profileFacade;

    @GetMapping("/orders")
    public String orders(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Orders");
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) {
            return "redirect:/login";
        }
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        List<OrderWithDetailsDto> ordersWithPayments = orderFacade.getOrdersWithPayments(user.getId());
        model.addAttribute("orders", ordersWithPayments != null ? ordersWithPayments : Collections.emptyList());
        return "order/orders";
    }

    @GetMapping("/orders/{orderId}")
    public String orderDetail(@PathVariable UUID orderId, Model model, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        OrderWithDetailsDto orderWithPayment = orderFacade.getOrderWithPayment(orderId);
        if (orderWithPayment.getOrder() == null) {
            model.addAttribute("error", "Order not found");
            return "redirect:/orders";
        }
        model.addAttribute("order", orderWithPayment.getOrder());
        model.addAttribute("payment", orderWithPayment.getPayment());
        return "order/detail";
    }

    @PostMapping("/orders")
    public String createOrder(@RequestBody OrderDto order, HttpServletRequest request,
                               Model model) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        OrderDto created = orderFacade.createOrder(order);
        if (created != null) {
            return "redirect:/orders/" + created.getId();
        }
        model.addAttribute("error", "Failed to create order");
        return "redirect:/checkout";
    }
}