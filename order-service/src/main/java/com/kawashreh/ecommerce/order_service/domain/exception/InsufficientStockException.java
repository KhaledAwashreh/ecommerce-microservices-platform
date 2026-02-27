package com.kawashreh.ecommerce.order_service.domain.exception;

public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requestedQuantity;
    private final int availableStock;

    public InsufficientStockException(String message) {
        super(message);
        this.productId = null;
        this.requestedQuantity = 0;
        this.availableStock = 0;
    }

    public InsufficientStockException(String productId, int requestedQuantity, int availableStock) {
        super(String.format("Insufficient stock for product %s: requested %d, available %d",
                productId, requestedQuantity, availableStock));
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableStock = availableStock;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableStock() {
        return availableStock;
    }
}
