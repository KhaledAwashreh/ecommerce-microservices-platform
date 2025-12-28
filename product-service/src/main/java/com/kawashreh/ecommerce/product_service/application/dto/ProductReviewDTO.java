package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ProductReviewDTO {
    private UUID productId;
    private UUID userId;
    private String review;
    private int stars;
}
