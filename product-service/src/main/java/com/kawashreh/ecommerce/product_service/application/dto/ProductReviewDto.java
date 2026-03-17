package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductReviewDto {

    private UUID id;

    private UUID userId;

    private UUID productId;

    private String review;

    private int stars;

    private Instant createdAt;

    private Instant updatedAt;

}
