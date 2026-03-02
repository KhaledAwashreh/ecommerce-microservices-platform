package com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.kawashreh.ecommerce.api_gateway.constants.ApiPaths;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping(ApiPaths.USER_BASE + ApiPaths.USER_BY_ID)
    UserDto retrieveUserById(@PathVariable("userId") UUID userId);

    @GetMapping(ApiPaths.USER_BASE)
    UserDto retrieveByUsername(@RequestParam String username);
}
