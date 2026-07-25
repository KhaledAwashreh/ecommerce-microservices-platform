package com.kawashreh.ecommerce.payment_service.infrastructure.http.client;

import com.kawashreh.ecommerce.payment_service.domain.exception.OrderServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderServiceErrorDecoder implements ErrorDecoder {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = String.format("Order Service returned error: %s %s",
                response.status(), response.reason());

        logger.error("Feign error calling {}: status={}, reason={}",
                methodKey, response.status(), response.reason());

        return switch (response.status()) {
            case 404 -> new OrderServiceException("Order not found", 404);
            case 400 -> new OrderServiceException("Bad request to Order Service: " + response.reason(), 400);
            case 503 -> new OrderServiceException("Order Service unavailable", 503);
            default -> new OrderServiceException(message, response.status());
        };
    }
}
