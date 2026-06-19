package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.RoleDto;
import com.kawashreh.ecommerce.frontend.dto.request.RoleRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "role-service-UI-client", url = "${api.gateway.base-url}/api/v1/roles")
public interface RoleServiceClient {

    @GetMapping
    List<RoleDto> getAll();

    @GetMapping("/{id}")
    RoleDto getById(@PathVariable("id") UUID id);

    @PostMapping
    RoleDto create(@RequestBody RoleRequest request);

    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") UUID id);
}
