package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.domain.service.dto.UserCreateRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserResponse;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserSearchRequest;
import com.kawashreh.ecommerce.user_service.domain.service.dto.UserUpdateRequest;

import java.util.List;
import java.util.UUID;

// TODO (investigate SpEL): Add @PreAuthorize/@PostAuthorize SpEL expressions for
//   ownership checks (e.g., #id == authentication.principal.id) once security
//   context is wired into the service layer, replacing manual checks.
public interface UserService {
    UserResponse create(UserCreateRequest request);

    List<UserResponse> getAll();

    UserResponse find(UUID id);

    UserResponse findByEmail(String email);

    UserResponse findByUsername(String username);

    void delete(UUID id, UUID requestingUserId);

    String login(String username, String password);

    UserResponse update(UUID id, UserUpdateRequest request);

    List<UserResponse> search(UserSearchRequest request);

    UserResponse changePassword(String username, String oldPassword, String newPassword);
}
