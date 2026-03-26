package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    @NonNull
    private UUID id;
    
    @NonNull
    private UUID storeId;
    
    @NonNull
    private UUID seller;
    
    @NonNull
    private UUID buyer;
    
    @NonNull
    private String status;
    
    @Builder.Default
    private List<OrderItemDto> selectedItems = new ArrayList<>();
    
    @Builder.Default
    private List<DiscountDto> discountsApplied = new ArrayList<>();
    
    @NonNull
    private Instant createdAt;
    
    @NonNull
    private Instant updatedAt;
    
    private UUID createdBy;
    private UUID updatedBy;
}
