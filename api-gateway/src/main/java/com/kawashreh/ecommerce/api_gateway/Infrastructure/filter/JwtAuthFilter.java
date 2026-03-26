package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client.ReactiveUserServiceClient;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final ReactiveUserServiceClient userServiceClient;
    private final JwtService jwtService;

    public JwtAuthFilter(ReactiveUserServiceClient userServiceClient, JwtService jwtService) {
        this.userServiceClient = userServiceClient;
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

        return Mono.fromCallable(() -> jwtService.extractUsername(token))
                .flatMap(username -> {
                    if (username == null) {
                        log.warn("Could not extract username from token");
                        return unauthorized(exchange);
                    }
                    return userServiceClient.retrieveByUsername(username)
                            .flatMap(userDetails -> {
                                if (jwtService.validateToken(token, userDetails)) {
                                    log.info("Token validated for user: {}", userDetails.getUsername());
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(userDetails, null, null);
                                    return chain.filter(exchange)
                                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                                }
                                log.warn("Token validation failed for user: {}", userDetails.getUsername());
                                return unauthorized(exchange);
                            });
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