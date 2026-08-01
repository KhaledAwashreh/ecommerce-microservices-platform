package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS is enforced at the gateway since it is the single ingress for
 * browser-facing clients. Wired into {@link SecurityConfig} via
 * {@code ServerHttpSecurity.cors(...)} so that Spring Security's CORS
 * WebFilter (ordered ahead of authentication/authorization,
 * see {@link org.springframework.security.config.web.server.SecurityWebFiltersOrder#CORS})
 * handles preflight OPTIONS requests before they hit the JWT auth filter
 * or the authenticated-by-default authorization rule.
 * <p>
 * Allowed origins default to the local frontend-service dev port and are
 * overridable via the {@code CORS_ALLOWED_ORIGINS} env var (comma-separated)
 * for other environments.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
