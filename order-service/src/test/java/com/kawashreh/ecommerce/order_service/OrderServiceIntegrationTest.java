package com.kawashreh.ecommerce.order_service;

import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.PaymentClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.PaymentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    // create() invokes payment as of issue #9. There is no payment-service in this
    // test's container set, so without this the Feign call fails with
    // UnknownHostException and every order is rolled back.
    @MockitoBean
    private PaymentClient paymentClient;

    private UUID productVariationId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID storeId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        productVariationId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        InventoryDto mockInventory = InventoryDto.builder()
                .productVariationId(productVariationId)
                .quantity(10)
                .warehouseLocation("WAREHOUSE-A")
                .build();

        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(mockInventory);
        when(productServiceClient.checkInventoryAvailability(eq(productVariationId), any(Integer.class)))
                .thenReturn(true);
        when(productServiceClient.deductInventory(eq(productVariationId), any(UUID.class), any(Integer.class)))
                .thenReturn(true);

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

    @Test
    void create_shouldSucceed_whenInventoryAvailable() {
        Order order = Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order result = orderService.create(order);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getBuyer()).isEqualTo(buyerId);
    }

    // Regression: OrderDto.id is @NonNull, so every real HTTP client is forced to send an
    // id on create - which OrderHttpMapper/OrderMapper carry straight onto the new
    // OrderEntity despite it being @GeneratedValue. A non-null id made Spring Data's
    // isNew() check treat the brand-new entity as already existing, so save() called
    // merge() instead of persist(), and Hibernate threw
    // ObjectOptimisticLockingFailureException trying to update a row that was never
    // inserted - every single real order create failed, 100% reproducible. The test above
    // never caught this because it builds an Order directly in Java and never sets
    // .id(...), which no real client can do. This test does what a real client is forced
    // to: supply a client-side id, exactly like OrderHttpMapper.toDomain(orderDto) does
    // for every real POST /api/v1/orders request.
    @Test
    void create_shouldSucceed_whenCallerSuppliesAClientSideId() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order result = orderService.create(order);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getId()).isNotNull();
    }

    @Test
    void create_shouldFail_whenInsufficientInventory() {
        InventoryDto lowInventory = InventoryDto.builder()
                .productVariationId(productVariationId)
                .quantity(1)
                .build();

        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(lowInventory);

        Order order = Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(5)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> orderService.create(order))
                .hasMessageContaining("Insufficient stock");
    }

    // Was create_shouldFail_whenProductNotFound, stubbing retrieveProduct to return null:
    // OrderServiceImpl.validateInventoryAvailability used to call
    // productServiceClient.retrieveProduct(item.getProductSku()) as an extra existence
    // check, but item.getProductSku() is a ProductVariation id, not a Product id, so that
    // call always hit the wrong endpoint (found live via a smoke test - every real order
    // create failed). The call was removed as broken and redundant; retrieveInventory
    // returning null is now the sole "does this item exist" check.
    @Test
    void create_shouldFail_whenInventoryNotFound() {
        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(null);

        Order order = Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> orderService.create(order))
                .hasMessageContaining("Inventory not found");
    }

    @Test
    void findByBuyer_shouldReturnOrders() {
        Order order = Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderService.create(order);

        List<Order> orders = orderService.findByBuyer(buyerId);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getBuyer()).isEqualTo(buyerId);
    }

    @Test
    void findByStatus_shouldReturnFilteredOrders() {
        Order order = Order.builder()
                .buyer(buyerId)
                .seller(sellerId)
                .storeId(storeId)
                .selectedItems(new ArrayList<>(List.of(
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderService.create(order);

        List<Order> confirmedOrders = orderService.findByStatus(OrderStatus.CONFIRMED);
        List<Order> cancelledOrders = orderService.findByStatus(OrderStatus.CANCELLED);

        assertThat(confirmedOrders).hasSize(1);
        assertThat(cancelledOrders).isEmpty();
    }
}