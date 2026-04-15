package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterDto {
    private UUID categoryId;
    private String brand;
    private Double minPrice;
    private Double maxPrice;
    private Double minRating;
    private String sortBy;
    private String sortOrder;
}