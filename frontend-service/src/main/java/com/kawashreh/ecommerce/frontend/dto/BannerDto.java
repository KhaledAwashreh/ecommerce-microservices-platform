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
public class BannerDto {
    private UUID id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String actionUrl;
    private String backgroundColor;
    private boolean active;
    private Instant validFrom;
    private Instant validUntil;
}