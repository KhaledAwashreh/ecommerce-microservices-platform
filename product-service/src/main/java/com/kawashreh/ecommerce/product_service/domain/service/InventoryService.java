package com.kawashreh.ecommerce.product_service.domain.service;

import com.kawashreh.ecommerce.product_service.domain.model.Inventory;

import java.util.UUID;

public interface InventoryService {

    Inventory findByProductVariationId(UUID productVariationId);

    boolean checkAvailability(UUID productVariationId, int quantity);

    boolean deductStock(UUID productVariationId, int quantity);

    boolean restoreStock(UUID productVariationId, int quantity);
}
