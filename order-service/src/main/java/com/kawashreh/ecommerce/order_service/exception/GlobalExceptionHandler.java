package com.kawashreh.ecommerce.order_service.exception;

import com.kawashreh.ecommerce.common.dto.ErrorResponse;
import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * GH #42: order-service previously had no {@code @ControllerAdvice} at all, so any
 * unhandled exception (including a missing-id update/delete) fell through to Spring
 * Boot's default error body instead of a clean, typed response. This mirrors the
 * pattern already used by user-service/frontend-service: {@code NoSuchElementException}
 * (from {@code common.exceptions}) maps to 404 with a {@code common.dto.ErrorResponse}
 * body. Kept intentionally minimal - only the exception type this fix introduces is
 * handled here; broader error-handling coverage for the module is out of scope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
