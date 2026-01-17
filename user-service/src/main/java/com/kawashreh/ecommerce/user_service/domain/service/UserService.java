package com.kawashreh.ecommerce.user_service.domain.service;

import com.kawashreh.ecommerce.user_service.domain.model.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User create(User user);

    List<User> getAll();

    User find(UUID id);

    User findByEmail(String email);

    User findByUsername(String username);

    void delete(UUID id);

    User Login(String username, String password);

    User changePassword(String username, String oldPassword, String newPassword) throws Exception;
}
