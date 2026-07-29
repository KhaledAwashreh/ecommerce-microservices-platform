package com.kawashreh.ecommerce.order_service.infrastructure.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the caller's Authorization bearer token onto outbound Feign calls to
 * other backend services (product-service, payment-service, user-service). Needed
 * because those services now validate the JWT themselves (GH #17) - without this,
 * every service-to-service Feign call order-service makes would be rejected with
 * 401, since these calls go direct by DNS name and previously carried no token at
 * all (see the comment this replaces in application.yml).
 */
@Component
public class IncomingAuthHeaderFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String authHeader = resolveIncomingAuthHeader();
        if (authHeader != null && !authHeader.isEmpty()) {
            template.header("Authorization", authHeader);
        }
    }

    private String resolveIncomingAuthHeader() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        return request.getHeader("Authorization");
    }
}
