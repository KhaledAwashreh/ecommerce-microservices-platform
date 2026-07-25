package com.kawashreh.ecommerce.payment_service.domain.exception;

/**
 * Raised whenever payment-service cannot obtain an authoritative order (missing order,
 * order-service unreachable, circuit open, unexpected error response, etc). Thrown to make
 * {@code processPayment} fail loudly instead of silently falling back to a zero/guessed
 * amount - see PaymentServiceImpl.resolveOrderAmount.
 */
public class OrderServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public OrderServiceException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public OrderServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public OrderServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
