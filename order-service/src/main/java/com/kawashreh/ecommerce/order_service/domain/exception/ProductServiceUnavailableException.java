package com.kawashreh.ecommerce.order_service.domain.exception;

/**
 * GH #63: raised by {@link com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceErrorDecoder}
 * specifically for a 503 response from product-service - a transient condition (the
 * downstream service is temporarily overloaded/restarting), as opposed to the other
 * {@link ProductServiceException} cases (404 "not found", 400 "bad request"), which are
 * permanent business-logic outcomes that retrying cannot fix.
 *
 * <p>This distinct type is what {@code resilience4j.retry.instances.product-service}'s
 * {@code retryExceptions} list keys on: only this exception (plus low-level I/O failures
 * like connect/read timeouts) should trigger a retry of {@code deductInventory}/
 * {@code restoreInventory}. Those two calls are safe to retry because product-service's
 * deduction ledger is idempotent per {@code orderItemId} (GH #30) - a repeated deduct/restore
 * for the same order item is a no-op rather than a double-deduction.
 */
public class ProductServiceUnavailableException extends ProductServiceException {

    private static final long serialVersionUID = 1L;

    public ProductServiceUnavailableException(String message, String productId, int statusCode) {
        super(message, productId, statusCode);
    }
}
