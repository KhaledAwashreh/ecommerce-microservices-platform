package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String name;
    private String username;
    private String email;
    private java.util.Date birthdate;
    private String phone;
    private String gender;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}
