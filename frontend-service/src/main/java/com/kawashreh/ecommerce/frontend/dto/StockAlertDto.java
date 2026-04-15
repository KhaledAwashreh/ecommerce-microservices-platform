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
public class StockAlertDto {
    private UUID productId;
    private UUID userId;
    private boolean active;
    private String emailNotification;
}