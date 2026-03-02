package com.kawashreh.ecommerce.api_gateway.constants;

public final class ApiPaths {

    private ApiPaths() {} // prevent instantiation

    // Fallback
    public static final String FALLBACK = "/fallback";

    // User Service
    public static final String USER_BASE = "/api/v1/user";
    public static final String USER_BY_ID = "/{userId}";
}
