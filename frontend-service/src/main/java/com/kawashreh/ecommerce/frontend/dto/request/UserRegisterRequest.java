package com.kawashreh.ecommerce.frontend.dto.request;

import lombok.Data;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

/**
 * Request DTO for user registration.
 * Must match user-service's UserRegisterDto expectations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {
    
    @NonNull
    private String name;
    
    @NonNull
    private String username;
    
    @NonNull
    private String email;
    
    @NonNull
    private Date birthdate;
    
    @NonNull
    private String phone;
    
    private String gender;
    
    @NonNull
    private String rawPassword;
}