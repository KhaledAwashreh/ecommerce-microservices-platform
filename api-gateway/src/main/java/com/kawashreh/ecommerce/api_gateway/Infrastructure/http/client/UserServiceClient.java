package com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-service", url = "/api/v1/user")
public interface UserServiceClient {

    @GetMapping("/{userId}")
    UserDto retrieveUserById(@PathVariable("userId") UUID userId);

    @GetMapping(params = "username")  // ✅ Specify the query parameter
    UserDto retrieveByUsername(@RequestParam String username);
}
