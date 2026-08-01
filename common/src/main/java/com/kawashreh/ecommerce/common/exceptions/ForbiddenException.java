package com.kawashreh.ecommerce.common.exceptions;

// GH #37: distinct from NoSuchElementException (404 - resource does not exist). This is
// for the "resource exists but the caller doesn't own/can't access it" case, which used
// to be thrown as NoSuchElementException and conflated with a real 404.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }

}
