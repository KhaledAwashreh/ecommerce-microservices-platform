package com.kawashreh.ecommerce.order_service.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.common.dto.ErrorResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Validates the JWT on every request that reaches this service directly (GH #17):
 * previously any caller with network reach to the pod bypassed authentication
 * entirely, since only the api-gateway validated tokens.
 * <p>
 * Once a token validates, this filter overwrites any X-User-ID/X-User-Name/
 * X-User-Role headers on the request with values derived from the token's verified
 * claims, so those headers can no longer be spoofed by a caller (GH #19's root
 * cause) even though this service does not currently use them for authorization.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtService.validateToken(token)) {
                unauthorized(response, "Invalid or expired token");
                return;
            }

            String username = jwtService.extractUsername(token);
            UUID userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);

            filterChain.doFilter(new VerifiedIdentityRequestWrapper(request, username, userId, role), response);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            unauthorized(response, "Invalid or expired token");
        }
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator");
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), message, LocalDateTime.now());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * Overrides the identity headers with values derived from the verified token, so
     * any raw X-User-ID/X-User-Name/X-User-Role sent by the caller is discarded.
     */
    private static class VerifiedIdentityRequestWrapper extends HttpServletRequestWrapper {
        private final String username;
        private final UUID userId;
        private final String role;

        VerifiedIdentityRequestWrapper(HttpServletRequest request, String username, UUID userId, String role) {
            super(request);
            this.username = username;
            this.userId = userId;
            this.role = role;
        }

        @Override
        public String getHeader(String name) {
            if ("X-User-ID".equalsIgnoreCase(name)) {
                return userId != null ? userId.toString() : null;
            }
            if ("X-User-Name".equalsIgnoreCase(name)) {
                return username;
            }
            if ("X-User-Role".equalsIgnoreCase(name)) {
                return role;
            }
            return super.getHeader(name);
        }
    }
}
