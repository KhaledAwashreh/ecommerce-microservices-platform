package com.kawashreh.ecommerce.order_service.application.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class DiscountDto {

    @NonNull
    private UUID id;

    @NonNull
    private String name;

    @NonNull
    private String code;

    private String description;

    @NonNull
    private Instant createdAt;

    @NonNull
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
