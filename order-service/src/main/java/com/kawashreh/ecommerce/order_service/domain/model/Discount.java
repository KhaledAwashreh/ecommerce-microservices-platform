package com.kawashreh.ecommerce.order_service.domain.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Component
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Discount {
    private UUID id;
    private String name;
    private String code;
    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;
}
