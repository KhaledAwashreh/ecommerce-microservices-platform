package com.kawashreh.ecommerce.product_service.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // Previously unvalidated (called out as a known gap in this module's ai_doc): a
    // review with 99 stars, or a negative rating, was accepted and persisted with a 201.
    // Found live via a smoke test posting stars=99.
    @Min(value = 1, message = "stars must be between 1 and 5")
    @Max(value = 5, message = "stars must be between 1 and 5")
    private int stars;

    private Instant createdAt;

    private Instant updatedAt;

}
