package com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/user/{userId}")
    UserDto retrieveUserById(@PathVariable("userId") UUID userId);

    @GetMapping("/api/v1/user")
    UserDto retrieveByUsername(@RequestParam String username);
}
