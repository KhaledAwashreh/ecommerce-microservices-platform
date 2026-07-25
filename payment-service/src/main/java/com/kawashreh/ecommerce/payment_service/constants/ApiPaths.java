package com.kawashreh.ecommerce.payment_service.constants;

public final class ApiPaths {

    private ApiPaths() {} // prevent instantiation

    public static final String BASE_PATH = "/api/v1/payment";
    public static final String PROCESS = "/process";
    public static final String PAYMENT_BY_ID = "/{paymentId}";
    public static final String PAYMENT_BY_ORDER = "/order/{orderId}";
    public static final String REFUND = "/{paymentId}/refund";

    // External service API paths (for Feign clients)
    public static final String ORDER_BASE = "/api/v1/orders";
    public static final String ORDER_BY_ID = "/{id}";
}
