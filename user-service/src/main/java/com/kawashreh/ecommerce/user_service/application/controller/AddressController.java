package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.domain.model.Address;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/Address")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping()
    public ResponseEntity<List<Address>> get() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getAll());

    }

    @GetMapping("/{addressId}")
    public ResponseEntity<Address> findById(@PathVariable UUID addressId) {
        Address address = service.find(addressId);
        return ResponseEntity.ok(address);
    }

    @PostMapping
    public ResponseEntity<Address> create(@RequestBody Address address) {

        service.save(address);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(address);
    }
}
