package com.kawashreh.ecommerce.order_service;

import com.kawashreh.ecommerce.order_service.domain.enums.CartStatus;
import com.kawashreh.ecommerce.order_service.domain.model.Cart;
import com.kawashreh.ecommerce.order_service.domain.model.CartItem;
import com.kawashreh.ecommerce.order_service.domain.service.CartService;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.PaymentClient;
import com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceClient;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for the cart path, which previously had none at the service layer -
 * only CartControllerTest, which mocks CartService entirely and so could never exercise
 * the JPA persistence behavior these tests cover.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CartServiceIntegrationTest {

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
    private CartService cartService;

    // Not used by the cart path, but the application context wires them for
    // OrderServiceImpl - without these the context fails to start in this test.
    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private PaymentClient paymentClient;

    private CartItem sampleItem(UUID clientSuppliedId) {
        return CartItem.builder()
                .id(clientSuppliedId)
                .productId(UUID.randomUUID())
                .productVariantId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .productSku("WIDGET-001")
                .productName("Test Widget")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(9.99))
                .lineTotal(BigDecimal.valueOf(19.98))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void getOrCreateActiveCart_createsOnceThenReturnsTheSameCart() {
        UUID userId = UUID.randomUUID();

        Cart first = cartService.getOrCreateActiveCart(userId);
        Cart second = cartService.getOrCreateActiveCart(userId);

        assertThat(first).isNotNull();
        assertThat(first.getId()).isNotNull();
        assertThat(first.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(second.getId())
                .as("a second call must return the existing ACTIVE cart, not create a duplicate")
                .isEqualTo(first.getId());
    }

    /**
     * Regression: CartItemEntity.id is @GeneratedValue, but CartItemDto.id is @NonNull, so
     * every real HTTP client is forced to send an id that CartItemMapper carried straight
     * onto the new entity. A non-null id made Spring Data's isNew() check treat the
     * brand-new entity as already existing, so save() called merge() instead of persist(),
     * and Hibernate threw ObjectOptimisticLockingFailureException trying to update a row
     * that was never inserted - adding any item to a cart failed 100% of the time. Found
     * live via a smoke test; CartControllerTest could never have caught it because it
     * mocks CartService rather than hitting a real database.
     */
    @Test
    void addItem_succeeds_whenCallerSuppliesAClientSideItemId() {
        UUID userId = UUID.randomUUID();
        Cart cart = cartService.getOrCreateActiveCart(userId);
        UUID clientSuppliedId = UUID.randomUUID();

        Cart updated = cartService.addItem(cart.getId(), sampleItem(clientSuppliedId));

        assertThat(updated).isNotNull();
        assertThat(updated.getCartItems()).hasSize(1);
        assertThat(updated.getCartItems().get(0).getId())
                .as("the persisted item must carry a real server-generated id")
                .isNotNull();
    }

    /**
     * Regression, two bugs found together live via a smoke test:
     * <ol>
     *   <li>{@code recalculateTotals} computed {@code subtotal} but never assigned
     *       {@code totalPrice} - nothing in this module ever set it except
     *       {@code clearCart} (to zero), so every cart reported a payable total of 0.00
     *       regardless of contents.</li>
     *   <li>Only the quantity-change endpoint called {@code recalculateTotals} at all, so
     *       adding an item left both figures stale - a freshly-filled cart showed 0.00.</li>
     * </ol>
     */
    @Test
    void addItem_recalculatesSubtotalAndTotalPrice() {
        UUID userId = UUID.randomUUID();
        Cart cart = cartService.getOrCreateActiveCart(userId);

        Cart updated = cartService.addItem(cart.getId(), sampleItem(UUID.randomUUID()));

        assertThat(updated.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(19.98));
        assertThat(updated.getTotalPrice())
                .as("totalPrice must be derived from subtotal, not left at 0.00")
                .isEqualByComparingTo(BigDecimal.valueOf(19.98));
    }

    @Test
    void removeItem_recalculatesTotalsBackDown() {
        UUID userId = UUID.randomUUID();
        Cart cart = cartService.getOrCreateActiveCart(userId);
        Cart withItem = cartService.addItem(cart.getId(), sampleItem(UUID.randomUUID()));
        UUID persistedItemId = withItem.getCartItems().get(0).getId();

        Cart afterRemove = cartService.removeItem(cart.getId(), persistedItemId);

        assertThat(afterRemove.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(afterRemove.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void addThenRemoveItem_leavesCartEmpty() {
        UUID userId = UUID.randomUUID();
        Cart cart = cartService.getOrCreateActiveCart(userId);

        Cart withItem = cartService.addItem(cart.getId(), sampleItem(UUID.randomUUID()));
        assertThat(withItem.getCartItems()).hasSize(1);
        UUID persistedItemId = withItem.getCartItems().get(0).getId();

        Cart afterRemove = cartService.removeItem(cart.getId(), persistedItemId);

        assertThat(afterRemove).isNotNull();
        assertThat(afterRemove.getCartItems()).isEmpty();
    }
}
