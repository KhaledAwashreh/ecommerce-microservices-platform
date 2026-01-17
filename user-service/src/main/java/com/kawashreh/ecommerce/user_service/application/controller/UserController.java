package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.application.dto.UserDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserLoginDto;
import com.kawashreh.ecommerce.user_service.application.mapper.UserHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import jakarta.ws.rs.QueryParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // GET all users
    @GetMapping
    public ResponseEntity<List<UserDto>> getAll() {
        List<UserDto> dtos = service.getAll()
                .stream()
                .map(UserHttpMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // GET user by ID
    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> findById(@PathVariable UUID userId) {
        User user = service.find(userId);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(UserHttpMapper.toDto(user));
    }

    @GetMapping
    public ResponseEntity<UserDto> findByUsername(@RequestParam String username) {
        User user = service.findByUsername(username);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(UserHttpMapper.toDto(user));
    }

    // DELETE user
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId) {
        service.delete(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> create(@RequestBody UserDto userDto) {
        // Map DTO -> domain
        User user = UserHttpMapper.toDomain(userDto);

        // Save
        User saved = service.create(user);

        // Map domain -> DTO
        UserDto savedDto = UserHttpMapper.toDto(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedDto);
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> Login(@RequestBody UserLoginDto userDto) {

        var user = service.Login(userDto.getUsername(), userDto.getPassword());

        return ResponseEntity.ok(UserHttpMapper.toDto(user));

    }

}
