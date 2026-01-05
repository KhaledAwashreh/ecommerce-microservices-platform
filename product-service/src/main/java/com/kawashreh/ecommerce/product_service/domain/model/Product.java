package com.kawashreh.ecommerce.product_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    private UUID id;

    private String name;

    private String description;

    private List<Category> categories;

    private Instant createdAt;

    private Instant updatedAt;

    private String thumbnailUrl;

}
