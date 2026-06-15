package com.kawashreh.ecommerce.user_service.domain.service.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {
    private String name;
    private String username;
    private String email;
    private Date birthdate;
    private String phone;
    private Gender gender;
    private String rawPassword;
}
