package com.kawashreh.ecommerce.api_gateway.Infrastructure.configuration;

import com.kawashreh.ecommerce.api_gateway.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the gateway's CORS wiring (see {@link CorsConfig} and
 * {@link SecurityConfig#securityWebFilterChain}) end to end: a browser
 * preflight from an allowed origin gets the expected Access-Control-*
 * response headers without needing a JWT, and a preflight from an
 * unrecognised origin is rejected.
 */
@AutoConfigureWebTestClient
class CorsConfigurationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void preflightFromAllowedOriginIsPermittedWithoutAuthentication() {
        // Absolute URI: the reactive CorsUtils same-origin check reads
        // request.getURI() directly, and a mock request built from a bare
        // relative path has no scheme/host, which trips its null-checks.
        webTestClient.options()
                .uri("http://api-gateway.local/api/v1/user/register")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() {
        webTestClient.options()
                .uri("http://api-gateway.local/api/v1/user/register")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requestWithoutOriginIsUnaffectedByCorsAndStillRequiresAuth() {
        // A same-origin/non-browser request (no Origin header) skips CORS
        // handling entirely and falls through to normal authorization rules,
        // proving CORS wiring didn't accidentally open up protected routes.
        webTestClient.get()
                .uri("/api/v1/orders/123")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
