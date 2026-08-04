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

    // GH #64: previously returned every address for every user with no auth or
    // ownership scoping at all. Scoped to the caller's own X-User-ID, same pattern
    // as /search (GH #59).
    @GetMapping
    public ResponseEntity<List<CreateAddressResponse>> getAll(@RequestHeader("X-User-ID") UUID requestingUserId) {
        return ResponseEntity.ok(AddressHttpMapper.toResponseList(service.getAll(requestingUserId)));
    }

    // Found during GH #64 review: this had no ownership scoping or X-User-ID header at
    // all - any authenticated user could read any other user's address by UUID. Same
    // IDOR class as GH #64/#59, scoped the same way.
    @GetMapping("/{addressId}")
    public ResponseEntity<CreateAddressResponse> findById(@PathVariable UUID addressId,
                                                           @RequestHeader("X-User-ID") UUID requestingUserId) {
        AddressResponse address = service.find(addressId, requestingUserId);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AddressHttpMapper.toResponse(address));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CreateAddressResponse>> search(
            @RequestHeader("X-User-ID") UUID requestingUserId,
            @RequestParam(required = false) String q) {
        var request = com.kawashreh.ecommerce.user_service.domain.service.dto.AddressSearchRequest.builder()
                .userId(requestingUserId)
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
