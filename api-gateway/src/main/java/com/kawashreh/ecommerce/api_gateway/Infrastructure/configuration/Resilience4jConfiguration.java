package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfiguration {

    /**
     * Fallback defaults for any circuit breaker id that isn't explicitly listed under
     * {@code resilience4j.circuitbreaker.instances} in the active YAML profile (see
     * {@code application.yml}). Kept numerically identical to
     * {@code resilience4j.circuitbreaker.configs.default} in {@code application.yml} — that
     * YAML block is what actually governs the named instances
     * (user-service/product-service/order-service/payment-service); this bean only matters
     * for an id that YAML doesn't cover. GH #52: previously this drifted from the YAML
     * default (missing {@code minimumNumberOfCalls}) and {@code application-local.yml} used a
     * third, different set of thresholds.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(2))
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .build())
                .build());
    }
}
