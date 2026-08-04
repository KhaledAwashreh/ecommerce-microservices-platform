package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or malformed Authorization header for path: {}", path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        // Self-contained: identity comes entirely from the token's own verified claims
        // (embedded at issuance by user-service), never from a callback to user-service.
        // This filter previously called ReactiveUserServiceClient.retrieveByUsername on
        // every request to fetch a UserDto to validate against - an unauthenticated
        // internal HTTP call that broke outright once user-service's own JwtAuthFilter
        // (GH #17) started requiring auth on every non-public path, since the gateway
        // never attached a token to its own internal lookup. That made every protected
        // route through the gateway fail with 401.
        return Mono.fromCallable(() -> jwtService.validateToken(token))
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("Token validation failed");
                        return unauthorized(exchange);
                    }
                    String username = jwtService.extractUsername(token);
                    UUID userId = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);
                    if (username == null || userId == null) {
                        log.warn("Token missing required claims (username/userId)");
                        return unauthorized(exchange);
                    }
                    log.info("Token validated for user: {}", username);
                    List<SimpleGrantedAuthority> authorities = role != null
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : List.of();
                    UserDto userDetails = UserDto.builder().id(userId).username(username).build();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(r -> r.header("X-User-Name", username)
                                            .header("X-User-ID", userId.toString())
                                            .header("X-User-Role", role != null ? role : ""))
                            .build();
                    return chain.filter(mutatedExchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .onErrorResume(e -> {
                    log.error("JWT filter error for path {}: {}", path, e.getMessage());
                    return unauthorized(exchange);
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicPath(String path) {
        return path.contains("/api/v1/user/register") ||
                path.contains("/api/v1/user/login")    ||
                path.contains("/actuator/health")       ||
                path.contains("/actuator/info")         ||
                path.contains("/actuator/metrics");
    }
}