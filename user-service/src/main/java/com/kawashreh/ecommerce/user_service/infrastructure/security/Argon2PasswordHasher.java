package com.kawashreh.ecommerce.user_service.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordHasher implements PasswordHasher {


    @Override
    public String encode(String rawPassword) {
        return "";
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return false;
    }
}
