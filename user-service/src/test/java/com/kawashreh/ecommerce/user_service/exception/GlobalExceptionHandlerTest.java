package com.kawashreh.ecommerce.user_service.exception;

import com.kawashreh.ecommerce.common.dto.ErrorResponse;
import com.kawashreh.ecommerce.common.exceptions.ForbiddenException;
import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.common.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// Regression test for GH #37: NoSuchElementException was used for both "resource does not
// exist" (a real 404) and "resource exists but you don't own it" (a 403-shaped condition),
// and also for failed login (a 401-shaped condition) - all three were indistinguishable and
// all mapped to 404. ForbiddenException/UnauthorizedException let the handler map each case
// to its own status deliberately.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFound_stillMapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new NoSuchElementException("missing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("missing");
    }

    @Test
    void forbidden_mapsTo403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleForbidden(new ForbiddenException("You can only delete your own account"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("You can only delete your own account");
    }

    @Test
    void unauthorized_mapsTo401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnauthorized(new UnauthorizedException("Invalid username or password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }
}
