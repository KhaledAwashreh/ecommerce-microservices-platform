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
public class ProductReviewDto {
    private UUID id;
    private UUID productId;
    private UUID userId;
    private String userName;
    private int rating;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
    private int helpfulCount;
    private boolean verifiedPurchase;
}