package com.kawashreh.ecommerce.user_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private String permissions;
    private boolean archived;
    private Instant archivedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
