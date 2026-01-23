package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.user_service.application.dto.UserDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserLoginDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserRegisterDto;
import com.kawashreh.ecommerce.user_service.application.mapper.UserHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.model.User;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.infrastructure.security.JwtService;
import com.kawashreh.ecommerce.user_service.infrastructure.security.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService service;
    private final PasswordHasher hasher;

    public UserController(UserService service, PasswordHasher hasher) {
        this.service = service;
        this.hasher = hasher;
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

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> findById(@PathVariable UUID userId) {
        User user = service.find(userId);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(UserHttpMapper.toDto(user));
    }

    @GetMapping(params = "username")
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
    public ResponseEntity<UserDto> create(@RequestBody UserRegisterDto userDto) {
        User user = UserHttpMapper.toDomain(userDto);

        User saved = service.create(user,hasher.encode(userDto.getRawPassword()) );

        UserDto savedDto = UserHttpMapper.toDto(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedDto);
    }

    @PostMapping("/login")
    public ResponseEntity<String> Login(@RequestBody UserLoginDto userDto) {
        User user = service.Login(userDto.getUsername(), userDto.getPassword());

        return Objects.nonNull(user) ?
                ResponseEntity.status(HttpStatus.ACCEPTED).body(JwtService.generateToken(user.getUsername()))
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

}
