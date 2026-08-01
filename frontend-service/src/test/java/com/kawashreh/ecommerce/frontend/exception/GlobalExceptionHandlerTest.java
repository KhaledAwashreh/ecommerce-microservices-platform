package com.kawashreh.ecommerce.frontend.exception;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for GH #36: extractMessage() assumed every Feign error body was
 * common.ErrorResponse's shape. That only holds for user-service - order-service,
 * product-service, and payment-service have no GlobalExceptionHandler and fall back to
 * Spring Boot's default error body, a different shape entirely. The real message from
 * those services was being swallowed and replaced with a generic "Service error" (or,
 * as it turns out, could throw a NullPointerException out of the handler - see the test
 * below using the ObjectMapper configuration Spring Boot actually injects in production,
 * i.e. FAIL_ON_UNKNOWN_PROPERTIES disabled).
 */
class GlobalExceptionHandlerTest {

    // Mirrors Spring Boot's actual Jackson auto-configuration (FAIL_ON_UNKNOWN_PROPERTIES
    // disabled), since that's the real ObjectMapper bean injected into this handler in
    // production - not a bare `new ObjectMapper()`.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndRegisterModules();

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(objectMapper);

    @Test
    void handleFeign_extractsRealMessage_fromUserServiceErrorResponseShape() {
        String body = "{\"status\":404,\"message\":\"Invalid username or password\","
                + "\"timestamp\":\"2026-08-01T12:00:00\"}";

        ModelAndView mv = handler.handleFeign(
                feignException(404, body, "/api/v1/user/login"),
                new MockHttpServletRequest("POST", "/login"));

        assertThat(mv.getViewName()).contains("Invalid+username+or+password");
    }

    @Test
    void handleFeign_fallsBackToStatusDefault_whenSpringDefaultErrorBodyHasNoMessage() {
        // order-service/product-service/payment-service's actual default error body shape
        // with server.error.include-message left at its Spring Boot default ("never") -
        // no "message" key at all, only timestamp/status/error/path.
        String body = "{\"timestamp\":\"2026-08-01T12:00:00.000+00:00\",\"status\":404,"
                + "\"error\":\"Not Found\",\"path\":\"/api/v1/orders/123\"}";

        ModelAndView mv = handler.handleFeign(
                feignException(404, body, "/api/v1/orders/123"),
                new MockHttpServletRequest("GET", "/orders/123"));

        assertThat(mv.getViewName()).contains("Resource+not+found");
    }

    @Test
    void handleFeign_extractsRealMessage_fromSpringDefaultErrorBodyWithMessage() {
        // Same shape, but with server.error.include-message enabled (or a downstream
        // service that sets the message explicitly) - the real message should surface
        // instead of falling back to a generic status default.
        String body = "{\"timestamp\":\"2026-08-01T12:00:00.000+00:00\",\"status\":404,"
                + "\"error\":\"Not Found\",\"message\":\"Order not found\","
                + "\"path\":\"/api/v1/orders/123\"}";

        ModelAndView mv = handler.handleFeign(
                feignException(404, body, "/api/v1/orders/123"),
                new MockHttpServletRequest("GET", "/orders/123"));

        assertThat(mv.getViewName()).contains("Order+not+found");
    }

    @Test
    void handleFeign_fallsBackToServiceError_whenBodyIsNotJson() {
        // api-gateway's fallback controller returns a plain-text body, not JSON.
        ModelAndView mv = handler.handleFeign(
                feignException(503, "Service Unavailable", "/api/v1/orders"),
                new MockHttpServletRequest("GET", "/orders"));

        assertThat(mv.getViewName()).contains("Service+error");
    }

    private FeignException feignException(int status, String body, String url) {
        Request request = Request.create(
                Request.HttpMethod.GET, url, Map.of(), new byte[0], StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status)
                .reason("Error")
                .headers(Map.of())
                .request(request)
                .body(body.getBytes(StandardCharsets.UTF_8))
                .build();
        return FeignException.errorStatus("Service#method", response);
    }
}
