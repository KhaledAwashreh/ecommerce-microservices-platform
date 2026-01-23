package com.kawashreh.ecommerce.api_gateway.Infrastructure.filter;

import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.client.UserServiceClient;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.http.dto.UserDto;
import com.kawashreh.ecommerce.api_gateway.Infrastructure.security.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements WebFilter {

    private final UserServiceClient userServiceClient;
    private final JwtService jwtService;

    public JwtAuthFilter(UserServiceClient userServiceClient, JwtService jwtService) {
        this.userServiceClient = userServiceClient;
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip filter for public endpoints
        if (path.equals("/api/v1/user/register") || path.equals("/api/v1/user/login")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                String username = jwtService.extractUsername(token);

                if (username != null) {
                    // Fetch user details (this would ideally be reactive too)
                    UserDto userDetails = userServiceClient.retrieveByUsername(username);

                    if (jwtService.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, null);

                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                    }
                }
            } catch (Exception e) {
                // Log error but continue
            }
        }

        return chain.filter(exchange);
    }
}