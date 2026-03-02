package com.kawashreh.ecommerce.order_service.constants;

public final class ApiPaths {

    private ApiPaths() {} // prevent instantiation

    // Order API
    public static final String ORDER_BASE = "/api/v1/orders";
    public static final String ORDER_BY_BUYER = "/buyer/{buyerId}";
    public static final String ORDER_BY_SELLER = "/seller/{sellerId}";
    public static final String ORDER_BY_STORE = "/store/{storeId}";
    public static final String ORDER_BY_STATUS = "/status/{status}";
    public static final String ORDER_BY_BUYER_AND_STORE = "/buyer/{buyerId}/store/{storeId}";
    public static final String ORDER_BY_SELLER_AND_STORE = "/seller/{sellerId}/store/{storeId}";
    public static final String ORDER_BY_BUYER_AND_STATUS = "/buyer/{buyerId}/status/{status}";

    // External service API paths (for Feign clients)
    public static final String PRODUCT_BASE = "/api/v1/product";
    public static final String PRODUCT_BY_ID = "/{productId}";
    public static final String INVENTORY_BASE = "/api/v1/inventory";
    public static final String INVENTORY_BY_VARIATION = "/product-variation/{productVariationId}";
    public static final String INVENTORY_AVAILABILITY = "/product-variation/{productVariationId}/availability";
    public static final String INVENTORY_DEDUCT = "/product-variation/{productVariationId}/deduct";
    public static final String INVENTORY_RESTORE = "/product-variation/{productVariationId}/restore";

    public static final String PAYMENT_BASE = "/api/v1/payment";
    public static final String PAYMENT_PROCESS = "/process";
    public static final String PAYMENT_BY_ID = "/{paymentId}";
    public static final String PAYMENT_BY_ORDER = "/order/{orderId}";
    public static final String PAYMENT_REFUND = "/{paymentId}/refund";
}
