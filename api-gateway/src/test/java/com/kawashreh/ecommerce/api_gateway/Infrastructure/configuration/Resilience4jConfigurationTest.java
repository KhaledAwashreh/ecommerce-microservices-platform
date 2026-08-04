package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the numeric thresholds {@link Resilience4jConfiguration#defaultCustomizer()} falls
 * back to for any circuit breaker id not explicitly listed under
 * {@code resilience4j.circuitbreaker.instances} in the active profile's YAML (GH #52).
 * These values must stay in lockstep with {@code resilience4j.circuitbreaker.configs.default}
 * in both {@code application.yml} and {@code application-local.yml} — this test exists so a
 * future edit to one and not the other regresses visibly here instead of only at runtime.
 */
class Resilience4jConfigurationTest {

    private final Customizer<ReactiveResilience4JCircuitBreakerFactory> customizer =
            new Resilience4jConfiguration().defaultCustomizer();

    @Test
    void appliesTheDocumentedDefaultThresholdsToAnUnlistedCircuitBreakerId() {
        ReactiveResilience4JCircuitBreakerFactory factory = new ReactiveResilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null);
        customizer.customize(factory);

        ReactiveCircuitBreaker circuitBreaker = factory.create("some-unlisted-service");
        StepVerifier.create(circuitBreaker.run(Mono.just("ok"), null))
                .expectNext("ok")
                .verifyComplete();

        CircuitBreakerConfig config = factory.getCircuitBreakerRegistry()
                .circuitBreaker("some-unlisted-service")
                .getCircuitBreakerConfig();

        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(10).toMillis());
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
    }
}
