package com.kawashreh.ecommerce.frontend.client;

import com.kawashreh.ecommerce.frontend.dto.AddressDto;
import com.kawashreh.ecommerce.frontend.dto.request.AddressRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for Address Service.
 * Address endpoints are exposed via user-service.
 */
@FeignClient(name = "address-service-UI-client", url = "${api.gateway.base-url}/api/v1/address")
public interface AddressServiceClient {

    @GetMapping
    List<AddressDto> getAddresses();

    @GetMapping("/{addressId}")
    AddressDto getAddressById(@PathVariable UUID addressId);

    @PostMapping
    AddressDto createAddress(@RequestBody AddressRequest addressRequest);

    @DeleteMapping("/{addressId}")
    Void deleteAddress(@PathVariable("addressId") UUID addressId);
}