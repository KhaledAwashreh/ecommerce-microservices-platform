package com.kawashreh.ecommerce.order_service.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating the quantity of an existing cart item
 * (PUT /api/v1/carts/user/{userId}/items/{itemId}). Deliberately narrow — a full
 * {@link CartItemDto} carries several {@code @NonNull} fields that a quantity-only
 * update has no reason to require the caller to resend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemUpdateRequest {
    private int quantity;
}
