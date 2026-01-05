package com.kawashreh.ecommerce.product_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductReview {

    private UUID id;

    private UUID userId;

    private Product product;

    private String review;

    private int stars;

}
