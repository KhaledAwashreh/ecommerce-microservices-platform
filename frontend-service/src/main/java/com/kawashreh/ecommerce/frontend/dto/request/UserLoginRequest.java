package com.kawashreh.ecommerce.frontend.dto.request;

import lombok.Data;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for user login.
 * Must match user-service's UserLoginDto expectations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {
    
    @NonNull
    private String username;
    
    @NonNull
    private String password;
}