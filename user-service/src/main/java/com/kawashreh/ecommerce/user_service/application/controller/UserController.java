package com.kawashreh.ecommerce.user_service.application.controller;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.user_service.application.dto.UserDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserRegisterDto;
import com.kawashreh.ecommerce.user_service.application.dto.UserUpdateRequest;
import com.kawashreh.ecommerce.user_service.application.mapper.UserHttpMapper;
import com.kawashreh.ecommerce.user_service.domain.service.UserService;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
import com.kawashreh.ecommerce.user_service.constants.ApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.BASE_PATH)
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAll() {
        List<UserDto> dtos = UserHttpMapper.toDtoList(service.getAll());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> findById(@PathVariable UUID userId) {
        UserResponse user = service.find(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserHttpMapper.toDto(user));
    }

    @GetMapping(params = "username")
    public ResponseEntity<UserDto> findByUsername(@RequestParam String username) {
        UserResponse user = service.findByUsername(username);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserHttpMapper.toDto(user));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> search(@RequestParam(required = false) String q) {
        List<UserDto> dtos = UserHttpMapper.toDtoList(
                service.search(UserHttpMapper.toSearchRequest(q)));
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> create(@RequestBody UserRegisterDto userDto) {
        UserResponse saved = service.create(UserHttpMapper.toCreateRequest(userDto));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserHttpMapper.toDto(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody com.kawashreh.ecommerce.user_service.application.dto.UserLoginDto userDto) {
        String token = service.login(userDto.getUsername(), userDto.getPassword());
        if (token == null) {
            throw new NoSuchElementException("Invalid username or password");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(token);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> update(@PathVariable UUID userId,
                                          @RequestBody UserUpdateRequest updateDto,
                                          @RequestHeader("X-User-ID") UUID requestingUserId) {
        var serviceRequest = UserHttpMapper.toUpdateRequest(updateDto, requestingUserId);
        UserResponse updated = service.update(userId, serviceRequest);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserHttpMapper.toDto(updated));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId,
                                       @RequestHeader("X-User-ID") UUID requestingUserId) {
        service.delete(userId, requestingUserId);
        return ResponseEntity.noContent().build();
    }
}
