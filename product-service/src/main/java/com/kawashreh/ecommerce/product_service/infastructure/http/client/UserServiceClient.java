package com.kawashreh.ecommerce.product_service.infastructure.http.client;


import com.kawashreh.ecommerce.product_service.infastructure.http.dto.UserDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.UUID;

@FeignClient(name = "user-service", url = "localhost:8080/api/v1/user")
public interface UserServiceClient {

    @GetMapping("/{userId}")
    UserDto retrieveUser(@PathVariable UUID userId);
}
