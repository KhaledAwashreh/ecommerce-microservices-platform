package com.kawashreh.ecommerce.frontend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for changing the quantity of an existing cart item
 * (PUT /api/v1/carts/user/{userId}/items/{itemId} on order-service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemUpdateRequest {
    private int quantity;
}
