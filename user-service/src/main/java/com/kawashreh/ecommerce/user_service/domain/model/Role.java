package com.kawashreh.ecommerce.user_service.domain.model;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String name;
    private String description;
    private String permissions;
    private boolean archived;
    private Instant archivedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
