package com.kawashreh.ecommerce.frontend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Request DTO for user registration.
 * Must match user-service's UserRegisterDto expectations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {
    
    private String name;
    
    private String username;
    
    private String email;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date birthdate;
    
    private String phone;
    
    private String gender;
    
    private String rawPassword;
}
