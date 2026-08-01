package com.kawashreh.ecommerce.order_service.domain.exception;

/**
 * Raised when a status-changing update is requested against an {@code Order} whose
 * current status does not permit the requested transition - e.g. moving backward from
 * CONFIRMED to PENDING, or skipping straight from PENDING to SHIPPED/DELIVERED without
 * the intervening states. Distinct from "not found": the order exists, but the
 * requested transition is not legal from its current status. See
 * {@code OrderServiceImpl.update} (GH #43).
 */
public class InvalidOrderStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
