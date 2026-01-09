package com.kawashreh.ecommerce.user_service.application.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {

    private UUID id;

    private String name;

    private String username;

    private String email;

    private Date birthdate;

    private String phone;

    private Gender gender;

    private Instant createdAt;

    private Instant updatedAt;

}
