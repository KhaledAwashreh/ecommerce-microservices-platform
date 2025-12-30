package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import jakarta.ws.rs.PathParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")

public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping()
    public ResponseEntity<List<User>> get() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getAll());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> findById(@PathVariable UUID userId) {
        User user = service.find(userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(user);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<User> delete(@PathVariable UUID userId) {
        service.delete(userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(null);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {

        service.create(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(user);
    }

}
