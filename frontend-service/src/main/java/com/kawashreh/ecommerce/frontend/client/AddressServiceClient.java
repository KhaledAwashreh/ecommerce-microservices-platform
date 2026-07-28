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
    AddressDto getAddressById(@PathVariable("addressId") UUID addressId);

    // GH #58: user-service already exposes this as GET /api/v1/address/search?userId=,
    // server-side scoped to the given user (unlike getAddresses(), which returns every
    // address for every user) - used to populate the checkout address selector and to
    // validate that a submitted addressId actually belongs to the calling user.
    @GetMapping("/search")
    List<AddressDto> searchAddresses(@RequestParam("userId") UUID userId);

    @PostMapping
    AddressDto createAddress(@RequestBody AddressRequest addressRequest,
                            @RequestHeader("X-User-ID") UUID userId);

    @PutMapping("/{addressId}")
    AddressDto updateAddress(@PathVariable("addressId") UUID addressId,
                             @RequestBody AddressRequest addressRequest,
                             @RequestHeader("X-User-ID") UUID userId);

    @DeleteMapping("/{addressId}")
    Void deleteAddress(@PathVariable("addressId") UUID addressId,
                       @RequestHeader("X-User-ID") UUID userId);
}