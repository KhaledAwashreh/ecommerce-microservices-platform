package com.kawashreh.ecommerce.order_service;

import com.kawashreh.ecommerce.order_service.dataAccess.repository.OrderRepository;
import com.kawashreh.ecommerce.order_service.domain.enums.OrderStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Order;
import com.kawashreh.ecommerce.order_service.domain.model.OrderItem;
import com.kawashreh.ecommerce.order_service.domain.service.OrderService;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.InventoryDto;
import com.kawashreh.ecommerce.order_service.infrastructure.http.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockBean
    private ProductServiceClient productServiceClient;

    private UUID productVariationId;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        productVariationId = UUID.randomUUID();
        buyerId = UUID.randomUUID();

        // Mock Product Service responses
        ProductDto mockProduct = ProductDto.builder()
                .id(productVariationId)
                .name("Test Product")
                .price(BigDecimal.valueOf(99.99))
                .stock(10)
                .build();

        InventoryDto mockInventory = InventoryDto.builder()
                .id(UUID.randomUUID())
                .productVariationId(productVariationId)
                .quantity(10)
                .reservedQuantity(0)
                .warehouseLocation("WAREHOUSE-A")
                .build();

        when(productServiceClient.retrieveProduct(any(UUID.class))).thenReturn(mockProduct);
        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(mockInventory);
        when(productServiceClient.checkInventoryAvailability(eq(productVariationId), any(Integer.class)))
                .thenReturn(true);
        when(productServiceClient.deductInventory(eq(productVariationId), any(Integer.class)))
                .thenReturn(true);
    }

    @Test
    void create_shouldSucceed_whenInventoryAvailable() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .storeId(UUID.randomUUID())
                .selectedItems(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order result = orderService.create(order);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getBuyer()).isEqualTo(buyerId);
    }

    @Test
    void create_shouldFail_whenInsufficientInventory() {
        // Mock insufficient inventory
        InventoryDto lowInventory = InventoryDto.builder()
                .id(UUID.randomUUID())
                .productVariationId(productVariationId)
                .quantity(1)
                .reservedQuantity(0)
                .build();

        when(productServiceClient.retrieveInventory(any(UUID.class))).thenReturn(lowInventory);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .storeId(UUID.randomUUID())
                .selectedItems(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(5)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            orderService.create(order);
        } catch (Exception e) {
            // Expected
            assertThat(e.getMessage()).contains("Insufficient stock");
        }
    }

    @Test
    void create_shouldFail_whenProductNotFound() {
        when(productServiceClient.retrieveProduct(any(UUID.class))).thenReturn(null);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .storeId(UUID.randomUUID())
                .selectedItems(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(UUID.randomUUID())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            orderService.create(order);
        } catch (Exception e) {
            // Expected
            assertThat(e.getMessage()).contains("Product not found");
        }
    }

    @Test
    void findByBuyer_shouldReturnOrders() {
        // Create an order first
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .storeId(UUID.randomUUID())
                .selectedItems(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                ))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderService.create(order);

        // Find by buyer
        List<Order> orders = orderService.findByBuyer(buyerId);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getBuyer()).isEqualTo(buyerId);
    }

    @Test
    void findByStatus_shouldReturnFilteredOrders() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyerId)
                .storeId(UUID.randomUUID())
                .selectedItems(List.of(
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
                ))
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
