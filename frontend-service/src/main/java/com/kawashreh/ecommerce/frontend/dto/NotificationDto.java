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
public class NotificationDto {
    private UUID id;
    private String userId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private Instant createdAt;
    private String actionUrl;
    
    public void markAsRead() {
        this.read = true;
    }
}