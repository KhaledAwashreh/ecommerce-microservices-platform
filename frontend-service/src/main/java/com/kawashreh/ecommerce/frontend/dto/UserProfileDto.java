package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private UUID id;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String gender;
    private Instant createdAt;
    private int orderCount;
    private String memberSince;
    private String membershipTier;
}