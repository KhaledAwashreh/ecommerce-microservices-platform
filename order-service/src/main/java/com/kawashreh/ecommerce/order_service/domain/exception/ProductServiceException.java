package com.kawashreh.ecommerce.order_service.domain.exception;

public class ProductServiceException extends RuntimeException {

    private final String productId;
    private final int statusCode;

    public ProductServiceException(String message) {
        super(message);
        this.productId = null;
        this.statusCode = 0;
    }

    public ProductServiceException(String message, String productId) {
        super(message);
        this.productId = productId;
        this.statusCode = 0;
    }

    public ProductServiceException(String message, String productId, int statusCode) {
        super(message);
        this.productId = productId;
        this.statusCode = statusCode;
    }

    public ProductServiceException(String message, Throwable cause) {
        super(message, cause);
        this.productId = null;
        this.statusCode = 0;
    }

    public String getProductId() {
        return productId;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
