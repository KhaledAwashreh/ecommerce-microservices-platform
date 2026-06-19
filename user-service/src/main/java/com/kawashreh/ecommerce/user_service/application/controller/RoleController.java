package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.application.dto.RoleRequest;
import com.kawashreh.ecommerce.user_service.application.dto.RoleResponse;
import com.kawashreh.ecommerce.user_service.application.mapper.RoleHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> getAll() {
        return ResponseEntity.ok(RoleHttpMapper.toResponseList(service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(RoleHttpMapper.toResponse(service.find(id)));
    }

    @PostMapping
    public ResponseEntity<RoleResponse> create(@RequestBody @Valid RoleRequest request) {
        var role = RoleHttpMapper.toDomain(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoleHttpMapper.toResponse(service.save(role)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
