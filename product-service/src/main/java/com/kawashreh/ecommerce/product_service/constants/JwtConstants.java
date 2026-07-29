package com.kawashreh.ecommerce.product_service.constants;

public final class JwtConstants {

    private JwtConstants() {} // prevent instantiation

    // Same shared signing secret used by api-gateway and user-service. Kept as a
    // hardcoded constant to match how this repo sources it today (GH #15 tracks
    // moving this to an env var across all services).
    public static final String SECRET = "5367566859703373367639792F423F452848284D6251655468576D5A71347437";
    public static final long EXPIRATION_TIME = 1000L * 60 * 30; // 30 minutes
}
