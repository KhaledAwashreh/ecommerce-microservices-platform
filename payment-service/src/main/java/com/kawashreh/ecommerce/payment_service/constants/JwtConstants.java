package com.kawashreh.ecommerce.payment_service.constants;

public final class JwtConstants {

    private JwtConstants() {} // prevent instantiation

    // Signing secret is intentionally NOT defined here. It is sourced from the
    // `jwt.secret` property (env var JWT_SECRET, no committed default) and injected
    // into JwtService via @Value, so a missing secret fails application startup
    // loudly instead of silently falling back to a hardcoded value.
    public static final long EXPIRATION_TIME = 1000L * 60 * 30; // 30 minutes
}
