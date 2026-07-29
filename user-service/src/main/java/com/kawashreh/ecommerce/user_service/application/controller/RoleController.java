package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.application.dto.RoleRequest;
import com.kawashreh.ecommerce.user_service.application.dto.RoleResponse;
import com.kawashreh.ecommerce.user_service.application.mapper.RoleHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.enums.UserRole;
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

    // GH #18: creating/deleting roles used to have no identity or permission check
    // at all - any authenticated caller could do either. X-User-Role is set by
    // JwtAuthFilter from the caller's verified token claims, so it cannot be
    // spoofed by the client.
    @PostMapping
    public ResponseEntity<RoleResponse> create(@RequestBody @Valid RoleRequest request,
                                                @RequestHeader(value = "X-User-Role", required = false) String requestingUserRole) {
        if (!isAdmin(requestingUserRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var role = RoleHttpMapper.toDomain(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoleHttpMapper.toResponse(service.save(role)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @RequestHeader(value = "X-User-Role", required = false) String requestingUserRole) {
        if (!isAdmin(requestingUserRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        service.delete(id);
        return ResponseEntity.ok().build();
    }

    private boolean isAdmin(String role) {
        return role != null && UserRole.ADMIN.name().equalsIgnoreCase(role);
    }
}
