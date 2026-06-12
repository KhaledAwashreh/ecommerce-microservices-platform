package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.AddressServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.request.AddressRequest;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.facade.ProfileWithAddressesDto;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.UUID;

@Controller
public class ProfileController {

    @Autowired private SessionManager sessionManager;
    @Autowired private ProfileFacade profileFacade;
    @Autowired private AddressServiceClient addressServiceClient;

    @GetMapping("/profile")
    public String profile(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Account");
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) {
            return "redirect:/login";
        }
        ProfileWithAddressesDto profile = profileFacade.getProfileWithAddresses(username);
        if (profile.getUser() == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", profile.getUser());
        model.addAttribute("addresses", profile.getAddresses());
        return "user/profile";
    }

    @GetMapping("/addresses")
    public String addresses(Model model, HttpServletRequest request) {
        model.addAttribute("title", "Your Addresses");
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        model.addAttribute("addresses", profileFacade.getAllAddresses());
        return "user/addresses";
    }

    @PostMapping("/addresses/add")
    public String addAddress(@ModelAttribute AddressRequest addressRequest, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        addressServiceClient.createAddress(addressRequest);
        return "redirect:/addresses";
    }

    @PostMapping("/addresses/delete")
    public String deleteAddress(@RequestParam UUID addressId, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        addressServiceClient.deleteAddress(addressId);
        return "redirect:/addresses";
    }
}