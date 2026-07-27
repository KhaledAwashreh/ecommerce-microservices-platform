package com.kawashreh.ecommerce.payment_service.domain.exception;

/**
 * Raised when a state-changing operation is requested against a {@code Payment} whose
 * current status makes that operation illegal - e.g. refunding a payment that is not
 * {@code COMPLETED} (including one that is already {@code REFUNDED}). Distinct from "not
 * found": the payment exists, but the requested transition is not allowed from its current
 * status. See {@code PaymentServiceImpl.refundPayment} (issue #12).
 */
public class InvalidPaymentStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
