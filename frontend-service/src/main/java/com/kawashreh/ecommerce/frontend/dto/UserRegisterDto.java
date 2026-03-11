package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {
    private String name;
    private String username;
    private String email;
    private java.util.Date birthdate;
    private String phone;
    private String gender;
    private String rawPassword;
}
