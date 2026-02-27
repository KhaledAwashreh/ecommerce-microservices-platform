package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.kawashreh.ecommerce.order_service.domain.exception.ProductServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductServiceErrorDecoder implements ErrorDecoder {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceErrorDecoder.class);
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = String.format("Product Service returned error: %s %s", 
                response.status(), response.reason());
        
        logger.error("Feign error calling {}: status={}, reason={}", 
                methodKey, response.status(), response.reason());

        return switch (response.status()) {
            case 404 -> new ProductServiceException(
                    "Product not found", 
                    extractProductIdFromMethodKey(methodKey), 
                    404);
            case 400 -> new ProductServiceException(
                    "Bad request to Product Service: " + response.reason(),
                    extractProductIdFromMethodKey(methodKey),
                    400);
            case 503 -> new ProductServiceException(
                    "Product Service unavailable",
                    extractProductIdFromMethodKey(methodKey),
                    503);
            default -> new ProductServiceException(
                    message,
                    extractProductIdFromMethodKey(methodKey),
                    response.status());
        };
    }

    private String extractProductIdFromMethodKey(String methodKey) {
        // Extract productId from method key like "ProductServiceClient#retrieveProduct(UUID)"
        // This is for logging purposes - the actual ID would need to be extracted from the request
        return "unknown";
    }
}
