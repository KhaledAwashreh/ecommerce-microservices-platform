package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class InventoryServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    private UUID productVariationId;

    @BeforeEach
    void setUp() {
        inventoryDeductionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariationRepository.deleteAll();
        productRepository.deleteAll();

        ProductEntity product = ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .ownerId(UUID.randomUUID())
                .build();

        product = productRepository.save(product);

        ProductVariationEntity variation = ProductVariationEntity.builder()
                .sku("TEST-SKU-001")
                .name("Test Variation")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(0)
                .isActive(true)
                .product(product)
                .build();

        variation = productVariationRepository.save(variation);
        productVariationId = variation.getId();

        InventoryEntity inventory = InventoryEntity.builder()
                .productVariation(variation)
                .quantity(10)
                .warehouseLocation("WAREHOUSE-A")
                .build();

        inventoryRepository.save(inventory);
    }

    @Test
    void findByProductVariationId_shouldReturnInventory() {
        var inventory = inventoryService.findByProductVariationId(productVariationId);

        assertThat(inventory).isNotNull();
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void checkAvailability_shouldReturnTrueWhenSufficientStock() {
        boolean available = inventoryService.checkAvailability(productVariationId, 5);

        assertThat(available).isTrue();
    }

    @Test
    void checkAvailability_shouldReturnFalseWhenInsufficientStock() {
        boolean available = inventoryService.checkAvailability(productVariationId, 15);

        assertThat(available).isFalse();
    }

    @Test
    void deductStock_shouldSucceedWhenSufficientStock() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 3);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void deductStock_shouldFailWhenInsufficientStock() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 15);

        assertThat(result).isFalse();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void deductStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.deductStock(UUID.randomUUID(), UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void restoreStock_shouldSucceed() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean result = inventoryService.restoreStock(productVariationId, orderItemId, 5);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restoreStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.restoreStock(UUID.randomUUID(), UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void deductStock_concurrentDeduction_shouldHandleRaceCondition() throws InterruptedException {
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).get();
        inventory.setQuantity(5);
        inventoryRepository.save(inventory);

        // Two different order items - this is testing the pessimistic lock's
        // serialization of the WHERE-guarded UPDATE, not per-item idempotency (Task 2's
        // idempotency check only no-ops a *repeated* orderItemId, which this deliberately
        // is not).
        Thread thread1 = new Thread(() -> inventoryService.deductStock(productVariationId, UUID.randomUUID(), 5));
        Thread thread2 = new Thread(() -> inventoryService.deductStock(productVariationId, UUID.randomUUID(), 5));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        var finalInventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(finalInventory.getQuantity()).isEqualTo(0);
    }

    // --- GH #28: ProductVariationEntity.stockQuantity must stay in sync with
    // Inventory.quantity, the only two stock-mutating operations in the module. ---

    @Test
    void deductStock_shouldKeepProductVariationStockQuantityInSync() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 3);
        assertThat(result).isTrue();

        var variation = productVariationRepository.findById(productVariationId).orElseThrow();
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).orElseThrow();

        assertThat(variation.getStockQuantity()).isEqualTo(inventory.getQuantity());
        assertThat(variation.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void restoreStock_shouldKeepProductVariationStockQuantityInSync() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean result = inventoryService.restoreStock(productVariationId, orderItemId, 5);
        assertThat(result).isTrue();

        var variation = productVariationRepository.findById(productVariationId).orElseThrow();
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).orElseThrow();

        assertThat(variation.getStockQuantity()).isEqualTo(inventory.getQuantity());
        assertThat(variation.getStockQuantity()).isEqualTo(10);
    }

    // --- GH #30 (lock/guard half, already fixed at 38595d6): restoreStock had no lock
    // and no guard, unlike deductStock. ---

    @Test
    void restoreStock_shouldRejectNonPositiveQuantity() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean zeroResult = inventoryService.restoreStock(productVariationId, orderItemId, 0);
        boolean negativeResult = inventoryService.restoreStock(productVariationId, orderItemId, -5);

        assertThat(zeroResult).isFalse();
        assertThat(negativeResult).isFalse();

        // A negative "restore" must never silently deduct stock.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(5);
    }

    // --- GH #30 (upper-bound half - the fix this plan adds): a ledger keyed by
    // orderItemId now bounds restoreStock and makes both deductStock and restoreStock
    // idempotent per orderItemId. ---

    @Test
    void deductStock_calledTwiceWithSameOrderItemId_isIdempotent() {
        UUID orderItemId = UUID.randomUUID();

        boolean first = inventoryService.deductStock(productVariationId, orderItemId, 3);
        boolean second = inventoryService.deductStock(productVariationId, orderItemId, 3);

        assertThat(first).isTrue();
        assertThat(second).isTrue();

        // Only the first call actually decremented stock - the retry must not double-deduct.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void restore_exceedingDeductedAmount_isRejected() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        // Partial restore within the ceiling succeeds.
        boolean partial = inventoryService.restoreStock(productVariationId, orderItemId, 3);
        assertThat(partial).isTrue();

        // 3 already restored + 3 more requested (6) exceeds the 5 that were deducted.
        boolean exceeding = inventoryService.restoreStock(productVariationId, orderItemId, 3);
        assertThat(exceeding).isFalse();

        // Quantity reflects only the successful partial restore: 10 - 5 + 3 = 8.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(8);
    }

    @Test
    void restore_calledTwiceAfterFullRestore_isIdempotentNoOp() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean first = inventoryService.restoreStock(productVariationId, orderItemId, 5);
        boolean second = inventoryService.restoreStock(productVariationId, orderItemId, 5);

        assertThat(first).isTrue();
        assertThat(second).isTrue();

        // The second call must not double-credit stock.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restore_withNoMatchingLedgerEntry_isRejected() {
        boolean result = inventoryService.restoreStock(productVariationId, UUID.randomUUID(), 5);

        assertThat(result).isFalse();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restoreStock_concurrentRestoreOfSameOrderItem_ceilingHoldsUnderLock() throws InterruptedException {
        // Deduct once, then race two threads each trying to restore the full deducted
        // amount for the SAME orderItemId. The lock must serialize them so only one
        // actually credits stock; the other hits the idempotent-no-op branch, not a
        // double-credit.
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        Thread thread1 = new Thread(() -> inventoryService.restoreStock(productVariationId, orderItemId, 5));
        Thread thread2 = new Thread(() -> inventoryService.restoreStock(productVariationId, orderItemId, 5));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        var finalInventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(finalInventory.getQuantity()).isEqualTo(10);

        var finalVariation = productVariationRepository.findById(productVariationId).orElseThrow();
        assertThat(finalVariation.getStockQuantity()).isEqualTo(10);
    }
}
