package com.kawashreh.ecommerce.product_service.constants;

public final class ApiPaths {

    private ApiPaths() {} // prevent instantiation

    // Product API
    public static final String PRODUCT_BASE = "/api/v1/product";

    // Inventory API
    public static final String INVENTORY_BASE = "/api/v1/inventory";
    public static final String PRODUCT_VARIATION = "/product-variation/{productVariationId}";
    public static final String PRODUCT_VARIATION_AVAILABILITY = "/product-variation/{productVariationId}/availability";
    public static final String PRODUCT_VARIATION_DEDUCT = "/product-variation/{productVariationId}/deduct";
    public static final String PRODUCT_VARIATION_RESTORE = "/product-variation/{productVariationId}/restore";
}
