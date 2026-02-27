package com.kawashreh.ecommerce.order_service.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    private Integer stock;  // Available inventory quantity

    private BigDecimal price;
}
