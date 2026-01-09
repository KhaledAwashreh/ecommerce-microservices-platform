package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {

    private UUID id;

    private UUID ownerId;

    private String name;

    private String description;

    private List<CategoryDto> categories;

    private Instant createdAt;

    private Instant updatedAt;

    private String thumbnailUrl;

}
