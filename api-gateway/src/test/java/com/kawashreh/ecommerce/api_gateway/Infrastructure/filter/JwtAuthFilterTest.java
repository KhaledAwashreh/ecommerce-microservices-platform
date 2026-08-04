package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit tests for JwtAuthFilter (GH #45, revised for the self-contained
 * rewrite below): api-gateway had only a trivial contextLoads() test and a
 * BaseIntegrationTest with no subclasses, so none of the request-level behavior
 * downstream services rely on for authentication was covered. This filter is called
 * out explicitly in the issue as one of the paths "worth real tests." Runs without
 * Docker/Testcontainers/a Spring context, following the same plain-unit-test pattern
 * already used elsewhere in this repo (e.g. PaymentServiceImplTest,
 * OrderControllerTest).
 * <p>
 * These tests previously mocked ReactiveUserServiceClient.retrieveByUsername to
 * stand in for a real call to user-service - which meant they never caught that the
 * real call broke outright once user-service's own JwtAuthFilter (GH #17) started
 * requiring auth on every non-public path (including the endpoint this filter used
 * to call unauthenticated). The filter is now self-contained (identity comes
 * entirely from the token's own verified claims, no callback to user-service), which
 * both fixes that break and removes the class of bug these tests could never have
 * caught: a mocked-away integration point silently diverging from the real one. See
 * the live smoke test that surfaced this for the full story.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private WebFilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService);
    }

    private ServerWebExchange exchangeFor(String path, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (authorizationHeader != null) {
            builder = builder.header("Authorization", authorizationHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void filter_bypassesAuth_forPublicPath() {
        ServerWebExchange exchange = exchangeFor("/api/v1/user/login", null);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void filter_returns401_whenAuthorizationHeaderMissing() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", null);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_returns401_whenAuthorizationHeaderNotBearer() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", "Basic dXNlcjpwYXNz");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_returns401_whenTokenFailsValidation() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", "Bearer bad-token");

        when(jwtService.validateToken("bad-token")).thenReturn(false);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_returns401_whenTokenMissingRequiredClaims() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", "Bearer claimless-token");

        when(jwtService.validateToken("claimless-token")).thenReturn(true);
        when(jwtService.extractUsername("claimless-token")).thenReturn("alice");
        when(jwtService.extractUserId("claimless-token")).thenReturn(null);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_forwardsRequestWithVerifiedIdentityHeaders_whenTokenValid() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", "Bearer good-token");
        UUID userId = UUID.randomUUID();

        when(jwtService.validateToken("good-token")).thenReturn(true);
        when(jwtService.extractUsername("good-token")).thenReturn("alice");
        when(jwtService.extractUserId("good-token")).thenReturn(userId);
        when(jwtService.extractRole("good-token")).thenReturn("ADMIN");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // The filter builds a NEW mutated exchange (exchange.mutate()...build()) and
        // forwards that to the chain - the original `exchange` reference is never
        // mutated in place, so the forwarded headers must be read off the captured
        // argument, not off `exchange` itself.
        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());

        // GH #19: the downstream identity headers must be derived from the verified
        // token/user lookup, not merely passed through.
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("alice");
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-ID")).isEqualTo(userId.toString());
        assertThat(forwarded.getValue().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }
}
