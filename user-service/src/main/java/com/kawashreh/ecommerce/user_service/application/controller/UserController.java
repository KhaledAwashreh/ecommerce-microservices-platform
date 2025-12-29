package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/User")

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

    @GetMapping()
    public ResponseEntity<User> findById(@RequestParam UUID id) {
        User user = service.find(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(user);
    }

}
