package com.kawashreh.ecommerce.user_service.infrastructure.security;


public interface PasswordHasher {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}