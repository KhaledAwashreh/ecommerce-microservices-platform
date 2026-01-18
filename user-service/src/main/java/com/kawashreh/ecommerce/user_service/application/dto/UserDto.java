package com.kawashreh.ecommerce.user_service.application.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.*;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {

    @NonNull
    private UUID id;

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

    @NonNull
    private Gender gender;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

}
