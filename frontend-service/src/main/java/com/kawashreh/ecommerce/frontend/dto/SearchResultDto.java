package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String thumbnailUrl;
    private String category;
    private double rating;
    private int reviewCount;
    private boolean inStock;
    private List<String> highlights;
}