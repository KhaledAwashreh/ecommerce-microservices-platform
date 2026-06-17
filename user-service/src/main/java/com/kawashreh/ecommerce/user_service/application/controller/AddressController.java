package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.application.dto.AddressUpdateRequest;
import com.kawashreh.ecommerce.user_service.application.dto.CreateAddressRequest;
import com.kawashreh.ecommerce.user_service.application.dto.CreateAddressResponse;
import com.kawashreh.ecommerce.user_service.application.mapper.AddressHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.AddressResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/address")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CreateAddressResponse>> getAll() {
        return ResponseEntity.ok(AddressHttpMapper.toResponseList(service.getAll()));
    }

    @GetMapping("/{addressId}")
    public ResponseEntity<CreateAddressResponse> findById(@PathVariable UUID addressId) {
        AddressResponse address = service.find(addressId);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AddressHttpMapper.toResponse(address));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CreateAddressResponse>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String q) {
        var request = com.kawashreh.ecommerce.user_service.domain.service.dto.AddressSearchRequest.builder()
                .userId(userId)
                .query(q)
                .build();
        return ResponseEntity.ok(AddressHttpMapper.toResponseList(service.search(request)));
    }

    @PostMapping
    public ResponseEntity<CreateAddressResponse> create(@RequestBody @Valid CreateAddressRequest request,
                                                         @RequestHeader("X-User-ID") UUID userId) {
        AddressResponse created = service.create(AddressHttpMapper.toCreateRequest(request, userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddressHttpMapper.toResponse(created));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<CreateAddressResponse> edit(@PathVariable UUID addressId,
                                                       @RequestBody @Valid AddressUpdateRequest updateDto,
                                                       @RequestHeader("X-User-ID") UUID requestingUserId) {
        updateDto.setId(addressId);
        var serviceRequest = AddressHttpMapper.toUpdateRequest(updateDto, requestingUserId);
        AddressResponse updated = service.update(addressId, serviceRequest);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AddressHttpMapper.toResponse(updated));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@PathVariable UUID addressId,
                                       @RequestHeader("X-User-ID") UUID requestingUserId) {
        service.delete(addressId, requestingUserId);
        return ResponseEntity.ok().build();
    }
}
