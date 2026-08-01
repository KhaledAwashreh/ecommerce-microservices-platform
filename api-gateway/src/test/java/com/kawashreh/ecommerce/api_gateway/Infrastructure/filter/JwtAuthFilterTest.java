package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client.ReactiveUserServiceClient;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain-Mockito unit tests for JwtAuthFilter (GH #45): api-gateway had only a trivial
 * contextLoads() test and a BaseIntegrationTest with no subclasses, so none of the
 * request-level behavior downstream services rely on for authentication was covered.
 * This filter is called out explicitly in the issue as one of the paths "worth real
 * tests." Runs without Docker/Testcontainers/a Spring context, following the same
 * plain-unit-test pattern already used elsewhere in this repo (e.g.
 * PaymentServiceImplTest, OrderControllerTest).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private ReactiveUserServiceClient userServiceClient;

    @Mock
    private JwtService jwtService;

    @Mock
    private WebFilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(userServiceClient, jwtService);
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
        UserDto userDetails = UserDto.builder().id(UUID.randomUUID()).username("alice").build();

        when(jwtService.extractUsername("bad-token")).thenReturn("alice");
        when(userServiceClient.retrieveByUsername("alice")).thenReturn(Mono.just(userDetails));
        when(jwtService.validateToken("bad-token", userDetails)).thenReturn(false);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_forwardsRequestWithVerifiedIdentityHeaders_whenTokenValid() {
        ServerWebExchange exchange = exchangeFor("/api/v1/order", "Bearer good-token");
        UUID userId = UUID.randomUUID();
        UserDto userDetails = UserDto.builder().id(userId).username("alice").build();

        when(jwtService.extractUsername("good-token")).thenReturn("alice");
        when(userServiceClient.retrieveByUsername("alice")).thenReturn(Mono.just(userDetails));
        when(jwtService.validateToken("good-token", userDetails)).thenReturn(true);
        when(jwtService.extractClaim(eq("good-token"), any(Function.class))).thenReturn("ADMIN");
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
