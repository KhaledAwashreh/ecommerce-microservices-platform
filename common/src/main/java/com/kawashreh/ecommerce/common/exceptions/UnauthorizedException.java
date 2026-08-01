package com.kawashreh.ecommerce.common.exceptions;

// GH #37: distinct from NoSuchElementException (404). This is for a failed
// authentication attempt (e.g. bad login credentials) - a 401 case, not a "not found" or
// "forbidden" one.
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

}
