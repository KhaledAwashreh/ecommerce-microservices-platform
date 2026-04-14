package com.kawashreh.ecommerce.frontend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDto {
    private UUID id;
    private UUID orderId;
    private String carrier;
    private String trackingNumber;
    private String status;
    private Instant estimatedDelivery;
    private Instant shippedAt;
    private Instant deliveredAt;
    private String currentLocation;
    
    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }
    
    public boolean isInTransit() {
        return "IN_TRANSIT".equals(status);
    }
}