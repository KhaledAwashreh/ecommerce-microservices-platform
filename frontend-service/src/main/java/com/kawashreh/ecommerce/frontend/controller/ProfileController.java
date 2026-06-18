package com.kawashreh.ecommerce.frontend.controller;

import com.kawashreh.ecommerce.frontend.client.AddressServiceClient;
import com.kawashreh.ecommerce.frontend.client.UserServiceClient;
import com.kawashreh.ecommerce.frontend.config.SessionManager;
import com.kawashreh.ecommerce.frontend.dto.request.AddressRequest;
import com.kawashreh.ecommerce.frontend.dto.AddressDto;
import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.facade.ProfileWithAddressesDto;
import com.kawashreh.ecommerce.frontend.dto.request.UserUpdateRequest;
import com.kawashreh.ecommerce.frontend.facade.ProfileFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
public class ProfileController {

    @Autowired private SessionManager sessionManager;
    @Autowired private ProfileFacade profileFacade;
    @Autowired private AddressServiceClient addressServiceClient;
    @Autowired private UserServiceClient userServiceClient;

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

    @GetMapping("/profile/edit")
    public String editProfile(Model model, HttpServletRequest request) {
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) {
            return "redirect:/login";
        }
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("title", "Edit Profile");
        model.addAttribute("user", user);
        return "user/edit";
    }

    @PostMapping("/profile/edit")
    public String updateProfile(@ModelAttribute UserUpdateRequest updateRequest,
                                 HttpServletRequest request) {
        String username = sessionManager.getUsername(request);
        if (!sessionManager.isAuthenticated(request) || username == null) {
            return "redirect:/login";
        }
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        updateRequest.setId(user.getId());
        userServiceClient.updateUser(user.getId(), updateRequest, user.getId());
        return "redirect:/profile";
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

    @GetMapping("/addresses/grid")
    public String addressGrid(Model model, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "user/address-grid :: grid";
        }
        model.addAttribute("addresses", profileFacade.getAllAddresses());
        return "user/address-grid :: grid";
    }

    @GetMapping("/addresses/modal")
    public String addressModal(@RequestParam(required = false) UUID id, Model model,
                                HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        if (id != null) {
            AddressDto address = addressServiceClient.getAddressById(id);
            model.addAttribute("address", address);
        }
        return "user/address-modal :: modal";
    }

    // TODO: Convert to event-based pattern (HX-Trigger + grid auto-refresh) instead
    //   of returning the grid fragment directly. This couples the form to the grid
    //   via hx-target/hx-swap on the form itself, preventing reuse of the success
    //   response for other purposes (e.g. toast notifications).
    @PostMapping(value = "/addresses/add", headers = "HX-Request=true")
    public String addAddressHtmx(@ModelAttribute AddressRequest addressRequest,
                                  HttpServletRequest request,
                                  Model model) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        String username = sessionManager.getUsername(request);
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        addressServiceClient.createAddress(addressRequest, user.getId());
        model.addAttribute("addresses", profileFacade.getAllAddresses());
        return "user/address-grid :: grid";
    }

    @PostMapping("/addresses/add")
    public String addAddress(@ModelAttribute AddressRequest addressRequest,
                              HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        String username = sessionManager.getUsername(request);
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        addressServiceClient.createAddress(addressRequest, user.getId());
        return "redirect:/addresses";
    }

    // TODO: Convert to event-based pattern (HX-Trigger + grid auto-refresh) instead
    //   of returning the grid fragment directly.
    @PostMapping(value = "/addresses/edit/{id}", headers = "HX-Request=true")
    public String editAddressHtmx(@PathVariable UUID id,
                                   @ModelAttribute AddressRequest addressRequest,
                                   HttpServletRequest request,
                                   Model model) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        String username = sessionManager.getUsername(request);
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        addressServiceClient.updateAddress(id, addressRequest, user.getId());
        model.addAttribute("addresses", profileFacade.getAllAddresses());
        return "user/address-grid :: grid";
    }

    @PostMapping("/addresses/delete")
    public String deleteAddress(@RequestParam UUID addressId, HttpServletRequest request) {
        if (!sessionManager.isAuthenticated(request)) {
            return "redirect:/login";
        }
        String username = sessionManager.getUsername(request);
        UserDto user = profileFacade.getUserByUsername(username);
        if (user == null || user.getId() == null) {
            return "redirect:/login";
        }
        addressServiceClient.deleteAddress(addressId, user.getId());
        return "redirect:/addresses";
    }
}
