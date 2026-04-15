package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDto {
    private UUID userId;
    private String username;
    private int unreadNotifications;
    private int cartItemCount;
    private int wishlistCount;
    private int orderCount;
    private List<RecommendedProductDto> recommendedProducts;
    private Map<String, Integer> recentlyViewed;
}