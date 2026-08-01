package com.kawashreh.ecommerce.user_service.application.dto;

import com.kawashreh.ecommerce.user_service.domain.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    @NonNull
    @NotBlank
    private String name;

    @NonNull
    @NotBlank
    private String username;

    @NonNull
    @NotBlank
    @Email
    private String email;

    @NonNull
    @NotNull
    private Date birthdate;

    @NonNull
    @NotBlank
    private String phone;

    private Gender gender;

    @NonNull
    @NotBlank
    private String rawPassword;
}
