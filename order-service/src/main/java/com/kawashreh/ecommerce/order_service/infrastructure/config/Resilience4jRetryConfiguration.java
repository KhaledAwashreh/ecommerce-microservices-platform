package com.kawashreh.ecommerce.order_service.infrastructure.config;

import com.kawashreh.ecommerce.order_service.domain.exception.ProductServiceUnavailableException;
import feign.RetryableException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * GH #63: programmatic classification for {@code resilience4j.retry.instances.product-service}
 * (maxAttempts/waitDuration still come from application.yml).
 *
 * <p>A plain YAML {@code retryExceptions} class list does not work here. This service has
 * {@code spring.cloud.openfeign.circuitbreaker.enabled: true}, so every Feign call already
 * runs wrapped in a Spring Cloud circuit breaker <em>underneath</em> the {@code @Retry} aspect
 * (see {@link com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient}).
 * That circuit breaker has no fallback configured, so on any failure it re-throws
 * {@code org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException} -
 * nesting the real cause (e.g. {@link ProductServiceUnavailableException}) underneath - rather
 * than letting the original exception propagate. A class-list {@code retryExceptions} only
 * inspects the exception the retry aspect directly catches, so it always saw
 * {@code NoFallbackAvailableException} and never matched, meaning the retry silently gave up
 * after a single attempt regardless of what was configured. This customizer instead walks the
 * full cause chain, so the classification survives that wrapping.
 */
@Configuration
public class Resilience4jRetryConfiguration {

    // RetryConfigCustomizer#customize takes the raw RetryConfig.Builder type (no generic
    // parameter), so retryOnException(Predicate<Throwable>) needs an unchecked cast to the
    // parameterized builder to call - safe here since retryOnException only stores/invokes
    // the predicate, it never depends on the builder's <T> (result) type parameter.
    @SuppressWarnings("unchecked")
    @Bean
    public RetryConfigCustomizer productServiceRetryCustomizer() {
        return RetryConfigCustomizer.of("product-service", builder -> {
            RetryConfig.Builder<Object> typedBuilder = (RetryConfig.Builder<Object>) builder;
            typedBuilder.retryOnException(Resilience4jRetryConfiguration::isTransientProductServiceFailure);
        });
    }

    /**
     * Retryable: {@link ProductServiceUnavailableException} (503 - product-service is
     * temporarily overloaded/restarting) and raw I/O failures below the HTTP layer
     * (connect/read timeout, connection refused, {@link RetryableException}). NOT retryable:
     * the base {@link com.kawashreh.ecommerce.order_service.domain.exception.ProductServiceException}
     * for 404/400 - those are permanent business outcomes retrying cannot fix.
     */
    private static boolean isTransientProductServiceFailure(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof ProductServiceUnavailableException
                    || t instanceof IOException
                    || t instanceof RetryableException) {
                return true;
            }
        }
        return false;
    }
}
