package com.kawashreh.ecommerce.user_service.application.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import jakarta.validation.constraints.Email;
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

    // GH #40: this is a partial update - a null email means "leave unchanged"
    // (UserServiceImpl.update only sets fields that are non-null), so this must stay
    // format-only. @Email allows null and only rejects a present-but-malformed value.
    @Email
    private String email;

    private String phone;

    private Date birthdate;

    private Gender gender;
}
