package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.UserDto;
import com.kawashreh.ecommerce.frontend.dto.request.UserLoginRequest;
import com.kawashreh.ecommerce.frontend.dto.request.UserRegisterRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign client for User Service.
 * Uses Kubernetes DNS for service discovery: http://user-service:8080
 */
@FeignClient(name = "user-service-UI-client", url = "${api.gateway.base-url}/api/v1/user")
public interface UserServiceClient{

    @PostMapping("/register")
    UserDto register(@RequestBody UserRegisterRequest registerRequest);

    @PostMapping("/login")
    String login(@RequestBody UserLoginRequest loginRequest);

    @GetMapping("/{userId}")
    UserDto getUserById(@PathVariable UUID userId);

    @GetMapping()
    UserDto getUserByUsername(@RequestParam("username") String username);
}
