package com.kawashreh.ecommerce.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// "test" profile supplies a test-only jwt.secret (application-test.yml) — production
// requires JWT_SECRET with no default, so the default profile alone can no longer
// resolve this context.
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
