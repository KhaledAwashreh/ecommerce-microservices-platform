package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private UUID id;
    
    private UUID storeId;
    
    private UUID seller;
    
    private UUID buyer;
    
    private String status;
    
    @Builder.Default
    private List<OrderItemDto> selectedItems = new ArrayList<>();
    
    @Builder.Default
    private List<DiscountDto> discountsApplied = new ArrayList<>();
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    private UUID createdBy;
    private UUID updatedBy;
    
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal taxTotal;
    private BigDecimal totalAmount;
}
