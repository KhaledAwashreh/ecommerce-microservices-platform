package com.kawashreh.ecommerce.api_gateway.constants;

public final class JwtConstants {

    private JwtConstants() {} // prevent instantiation

    public static final String SECRET = "5367566859703373367639792F423F452848284D6251655468576D5A71347437";
    public static final long EXPIRATION_TIME = 1000L * 60 * 30; // 30 minutes
}
