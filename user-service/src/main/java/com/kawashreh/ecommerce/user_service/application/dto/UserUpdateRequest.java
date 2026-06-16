package com.kawashreh.ecommerce.user_service.application.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private UUID id;

    private String name;

    private String email;

    private String phone;

    private Date birthdate;

    private Gender gender;
}
