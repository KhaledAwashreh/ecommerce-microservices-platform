package com.kawashreh.ecommerce.product_service.application.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDto {

    private UUID id;

    private String name;

    private String description;

}
