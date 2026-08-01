package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Backs the gateway's {@code RequestRateLimiter} filter (see
 * {@code spring.cloud.gateway.default-filters} in application.yml), which
 * uses Spring Cloud Gateway's built-in {@code RedisRateLimiter} against the
 * Redis instance already wired in via the module's cache configuration.
 * <p>
 * Requests are throttled per client IP so a single caller cannot exhaust
 * capacity for everyone else. There is no per-user key today because
 * unauthenticated endpoints (e.g. login/register) must be rate limited too,
 * before a JWT identity exists.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .defaultIfEmpty("unknown");
    }
}
