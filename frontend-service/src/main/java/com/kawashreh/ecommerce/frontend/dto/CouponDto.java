package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDto {
    private UUID id;
    private String code;
    private String description;
    private String discountType;
    private double discountValue;
    private double minOrderAmount;
    private Instant validFrom;
    private Instant validUntil;
    private int maxUses;
    private int currentUses;
    private boolean active;
    
    public boolean isValid() {
        return active && Instant.now().isBefore(validUntil);
    }
}