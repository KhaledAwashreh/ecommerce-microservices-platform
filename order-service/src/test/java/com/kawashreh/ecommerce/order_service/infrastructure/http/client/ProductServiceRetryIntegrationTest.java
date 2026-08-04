package com.kawashreh.ecommerce.order_service.infrastructure.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GH #63: proves resilience4j.retry.instances.product-service is no longer dead config -
 * that {@code @Retry(name = "product-service")} on {@link ProductServiceClient#deductInventory}
 * actually causes a retry through the real Feign-generated, Resilience4j-wrapped bean.
 *
 * <p>Unlike {@code OrderServiceIntegrationTest}, {@code ProductServiceClient} is NOT
 * mocked with {@code @MockitoBean} here - a Mockito mock replaces the bean entirely and
 * bypasses the AOP proxy the retry aspect installs around it, so it could never exercise
 * real retry behavior. Instead this test points the Feign client at a real (if minimal)
 * HTTP server via {@code spring.cloud.openfeign.client.config.product-service.url}, so the
 * full stack - Feign, the error decoder, the circuit breaker, and the retry aspect - all
 * run for real.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ProductServiceRetryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb")
            .withUsername("test")
            .withPassword("test");

    static HttpServer productServiceStub;
    static int stubPort;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // HttpServer can only be start()-ed once in its lifetime (the JDK type does not
        // support stop()-then-start() reuse), so it is started exactly once here rather
        // than per-test; per-test isolation instead comes from add/removeContext in
        // setUp/tearDown below.
        productServiceStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubPort = productServiceStub.getAddress().getPort();
        productServiceStub.setExecutor(null);
        productServiceStub.start();
        registry.add("spring.cloud.openfeign.client.config.product-service.url",
                () -> "http://localhost:" + stubPort);
    }

    @AfterAll
    static void stopServer() {
        productServiceStub.stop(0);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    // Only PaymentClient is mocked - see class Javadoc for why ProductServiceClient must
    // stay real for this test.
    @MockitoBean
    private com.kawashreh.ecommerce.order_service.infrastructure.http.client.PaymentClient paymentClient;

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private UUID productVariationId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID storeId;
    private AtomicInteger deductCallCount;
    private int deductFailuresBeforeSuccess;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // The "product-service" CircuitBreaker is a singleton bean shared by the whole
        // Spring context (and thus across test methods in this class, since the context is
        // cached). Reset it before every test so one test's failures can't trip the
        // breaker open and short-circuit (CallNotPermittedException, which is not in
        // retryExceptions and so would silently stop the retry loop) for a later test.
        circuitBreakerRegistry.circuitBreaker("product-service").reset();

        productVariationId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        deductCallCount = new AtomicInteger(0);
        deductFailuresBeforeSuccess = 0;

        productServiceStub.createContext("/api/v1/product/", this::handleRetrieveProduct);
        productServiceStub.createContext("/api/v1/inventory/product-variation/", this::handleInventoryPath);

        when(paymentClient.processPayment(any(PaymentDto.class))).thenAnswer(invocation -> {
            PaymentDto request = invocation.getArgument(0);
            return PaymentDto.builder()
                    .id(UUID.randomUUID())
                    .orderId(request.getOrderId())
                    .buyerId(request.getBuyerId())
                    .paymentMethod(request.getPaymentMethod())
                    .status(PaymentDto.PaymentStatus.COMPLETED)
                    .build();
        });
    }

    @AfterEach
    void tearDown() {
        productServiceStub.removeContext("/api/v1/product/");
        productServiceStub.removeContext("/api/v1/inventory/product-variation/");
    }

    private void handleRetrieveProduct(HttpExchange exchange) throws IOException {
        ProductDto product = ProductDto.builder()
                .id(productVariationId)
                .name("Test Product")
                .price(BigDecimal.valueOf(19.99))
                .stock(100)
                .build();
        writeJson(exchange, 200, product);
    }

    private void handleInventoryPath(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/availability")) {
            writeJson(exchange, 200, Boolean.TRUE);
        } else if (path.endsWith("/deduct")) {
            handleDeduct(exchange);
        } else if (path.endsWith("/restore")) {
            writeJson(exchange, 200, Boolean.TRUE);
        } else {
            InventoryDto inventory = InventoryDto.builder()
                    .productVariationId(productVariationId)
                    .quantity(50)
                    .warehouseLocation("WAREHOUSE-A")
                    .build();
            writeJson(exchange, 200, inventory);
        }
    }

    private void handleDeduct(HttpExchange exchange) throws IOException {
        int attempt = deductCallCount.incrementAndGet();
        if (attempt <= deductFailuresBeforeSuccess) {
            // Simulate product-service being transiently unavailable.
            String body = "Service Unavailable";
            exchange.sendResponseHeaders(503, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        writeJson(exchange, 200, Boolean.TRUE);
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Order buildOrder() {
        return Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(19.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void deductInventory_retriesOnTransientFailure_andEventuallySucceeds() {
        // First two calls to deduct return 503 (transient); the third succeeds. If
        // @Retry(name = "product-service") were not actually wired, the order would fail
        // and roll back to CANCELLED after the very first 503.
        deductFailuresBeforeSuccess = 2;

        Order result = orderService.create(buildOrder());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(deductCallCount.get())
                .as("deductInventory should have been called 3 times (2 failures + 1 success), " +
                        "proving the retry aspect is actually wired, not just configured")
                .isEqualTo(3);
    }

    @Test
    void deductInventory_exhaustsRetries_andOrderIsCancelled_whenAlwaysUnavailable() {
        // Always 503 -> maxAttempts (3) is exhausted -> order creation fails and is
        // compensated (CANCELLED), same as any other inventory-update failure.
        deductFailuresBeforeSuccess = Integer.MAX_VALUE;

        try {
            orderService.create(buildOrder());
        } catch (RuntimeException expected) {
            // create() wraps and rethrows on failure - expected here.
        }

        assertThat(deductCallCount.get())
                .as("maxAttempts is 3, so exactly 3 attempts should have been made before giving up")
                .isEqualTo(3);
    }
}
