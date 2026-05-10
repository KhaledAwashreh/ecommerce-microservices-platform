package com.kawashreh.ecommerce.frontend.dto.facade;

import com.kawashreh.ecommerce.frontend.dto.CategoryDto;
import com.kawashreh.ecommerce.frontend.dto.ProductDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithDetailsDto {
    private ProductDto product;
    private CategoryDto category;
}