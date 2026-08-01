package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private final KeyResolver keyResolver = new RateLimiterConfig().ipKeyResolver();

    @Test
    void resolvesKeyFromRemoteAddress() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/product/1")
                        .remoteAddress(new InetSocketAddress("203.0.113.7", 54321)));

        StepVerifier.create(keyResolver.resolve(exchange))
                .assertNext(key -> assertThat(key).isEqualTo("203.0.113.7"))
                .verifyComplete();
    }

    @Test
    void fallsBackToUnknownWhenRemoteAddressIsMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/product/1"));

        StepVerifier.create(keyResolver.resolve(exchange))
                .assertNext(key -> assertThat(key).isEqualTo("unknown"))
                .verifyComplete();
    }
}
