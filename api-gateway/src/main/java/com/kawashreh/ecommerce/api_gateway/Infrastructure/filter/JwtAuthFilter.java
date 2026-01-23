package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client.ReactiveUserServiceClient;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final ReactiveUserServiceClient userServiceClient;
    private final JwtService jwtService;

    public JwtAuthFilter(ReactiveUserServiceClient userServiceClient, JwtService jwtService) {
        this.userServiceClient = userServiceClient;
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        logger.debug("JwtAuthFilter processing path: {}", path);

        if (path.contains("/api/v1/user/register") || path.contains("/api/v1/user/login")) {
            logger.debug("Path is public, skipping JWT validation");
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.debug("No Authorization header or invalid format");
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);
        logger.debug("Token extracted, length: {}", token.length());
        
        return Mono.fromCallable(() -> jwtService.extractUsername(token))
                .doOnNext(username -> logger.debug("Extracted username from token: {}", username))
                .flatMap(username -> {
                    if (username == null) {
                        logger.warn("Username is null after extraction");
                        return chain.filter(exchange);
                    }
                    logger.debug("Fetching user details for username: {}", username);
                    return userServiceClient.retrieveByUsername(username, token)
                            .doOnNext(userDetails -> logger.debug("Retrieved user details: {}", userDetails.getUsername()))
                            .flatMap(userDetails -> {
                                if (jwtService.validateToken(token, userDetails)) {
                                    logger.info("Token validated successfully for user: {}", userDetails.getUsername());
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(userDetails, null, null);
                                    return chain.filter(exchange)
                                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                                }
                                logger.warn("Token validation failed for user: {}", userDetails.getUsername());
                                return chain.filter(exchange);
                            })
                            .onErrorResume(e -> {
                                logger.error("Error retrieving user details: {}", e.getMessage(), e);
                                return chain.filter(exchange);
                            });
                })
                .onErrorResume(e -> {
                    logger.error("Error in JWT filter: {}", e.getMessage(), e);
                    return chain.filter(exchange);
                });
    }

}




