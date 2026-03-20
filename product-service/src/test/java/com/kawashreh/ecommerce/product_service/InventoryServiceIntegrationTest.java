package com.kawashreh.ecommerce.product_service;

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
    private ProductRepository productRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    private UUID productVariationId;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        productVariationRepository.deleteAll();
        productRepository.deleteAll(); // ✅ added cleanup

        // ✅ FIX: set ownerId (required by DB)
        ProductEntity product = ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .ownerId(UUID.randomUUID()) // 🔥 REQUIRED FIX
                .build();

        product = productRepository.save(product);

        // Create test product variation
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

        // Create inventory record
        InventoryEntity inventory = InventoryEntity.builder()
                .productVariation(variation)
                .quantity(10)
                .reservedQuantity(0)
                .warehouseLocation("WAREHOUSE-A")
                .build();

        inventoryRepository.save(inventory);
    }

    @Test
    void findByProductVariationId_shouldReturnInventory() {
        var inventory = inventoryService.findByProductVariationId(productVariationId);

        assertThat(inventory).isNotNull();
        assertThat(inventory.getQuantity()).isEqualTo(10);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
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
        boolean result = inventoryService.deductStock(productVariationId, 3);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void deductStock_shouldFailWhenInsufficientStock() {
        boolean result = inventoryService.deductStock(productVariationId, 15);

        assertThat(result).isFalse();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void deductStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.deductStock(UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void restoreStock_shouldSucceed() {
        inventoryService.deductStock(productVariationId, 5);

        boolean result = inventoryService.restoreStock(productVariationId, 5);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restoreStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.restoreStock(UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void deductStock_concurrentDeduction_shouldHandleRaceCondition() throws InterruptedException {
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).get();
        inventory.setQuantity(5);
        inventoryRepository.save(inventory);

        Thread thread1 = new Thread(() -> inventoryService.deductStock(productVariationId, 5));
        Thread thread2 = new Thread(() -> inventoryService.deductStock(productVariationId, 5));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        var finalInventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(finalInventory.getQuantity()).isEqualTo(0);
    }
}