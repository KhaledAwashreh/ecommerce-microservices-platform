package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDto {
    private int totalOrders;
    private int pendingOrders;
    private int completedOrders;
    private BigDecimal totalSpent;
    private BigDecimal averageOrderValue;
}