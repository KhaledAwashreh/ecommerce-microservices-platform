package com.kawashreh.ecommerce.frontend.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign interceptor that automatically attaches the session JWT as a Bearer token
 * on every outbound client request. Controllers no longer need to pass tokens manually.
 *
 * Requires that Spring's RequestContextListener (or DispatcherServlet) is active
 * so RequestContextHolder can resolve the current request — this is the default
 * in any Spring MVC application.
 */
@Component
public class BearerTokenInterceptor implements RequestInterceptor {

    private final SessionManager sessionManager;

    public BearerTokenInterceptor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void apply(RequestTemplate template) {
        String token = resolveToken();
        if (token != null && !token.isEmpty()) {
            template.header("Authorization", "Bearer " + token);
        }
    }

    private String resolveToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;

        HttpServletRequest request = attributes.getRequest();
        return sessionManager.getToken(request);
    }
}