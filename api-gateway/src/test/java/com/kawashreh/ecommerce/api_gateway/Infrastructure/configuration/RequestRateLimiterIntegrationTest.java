package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import com.kawashreh.ecommerce.api_gateway.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code RequestRateLimiter} default-filter configured in
 * application.yml is backed by a real, working {@link RedisRateLimiter}
 * against the Redis instance already wired into the gateway: burstCapacity
 * is 40 tokens (replenishRate 20/s) for the "user-service" route (see
 * {@code spring.cloud.gateway.default-filters}), so exhausting the bucket
 * for a fresh key must eventually deny a request.
 * <p>
 * Talks to the {@link RedisRateLimiter} bean directly rather than firing
 * real HTTP traffic through the gateway, so the assertion is about the
 * rate limiter's behavior specifically, not proxying/networking.
 */
class RequestRateLimiterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Test
    void tokenBucketDeniesRequestsOnceBurstCapacityIsExhausted() {
        String routeId = "user-service";
        String key = UUID.randomUUID().toString();

        int allowedCount = 0;
        RateLimiter.Response lastResponse = null;
        for (int i = 0; i < 60; i++) {
            lastResponse = redisRateLimiter.isAllowed(routeId, key).block();
            assertThat(lastResponse).isNotNull();
            if (lastResponse.isAllowed()) {
                allowedCount++;
            }
            else {
                break;
            }
        }

        assertThat(lastResponse.isAllowed())
                .as("expected the token bucket (burstCapacity=40) to eventually reject a fresh key hammered with 60 sequential requests")
                .isFalse();
        assertThat(allowedCount)
                .as("burstCapacity is 40, so no more than that many requests should be allowed before the first rejection")
                .isLessThanOrEqualTo(40);
    }
}
