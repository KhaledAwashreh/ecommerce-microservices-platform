package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.constants.CacheConstants;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductReviewRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductVariationMapper;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression coverage for issue #2: {@code @CacheEvict(key = "#result.productId")} on the
 * void {@code update}/{@code delete} methods failed SpEL evaluation on every call
 * ({@code #result} is null for void methods, and {@code ProductVariation} has no
 * {@code productId} property in the first place).
 * <p>
 * No controller exposes {@code ProductVariationService} yet (see the separate
 * ProductVariation-CRUD-unreachable issue), so this is exercised at the service level
 * against the real Spring caching proxy. The Redis-backed {@link CacheManager} is swapped
 * for an in-memory one so the test only depends on Postgres (via {@link BaseIntegrationTest}'s
 * Testcontainers setup), not on a running Redis instance.
 * <p>
 * Note: {@code productVariationService.find}/{@code findByProductId} are deliberately not
 * used here to populate/read the cache — they hit an unrelated, pre-existing bug (missing
 * {@code @Transactional}, so mapping the lazily-loaded {@code attributes} collection after
 * the repository call returns throws {@code LazyInitializationException}). That bug is out
 * of scope for issue #2, so eviction is verified directly against the {@link CacheManager}.
 */
@ActiveProfiles("test")
class ProductVariationServiceIntegrationTest extends BaseIntegrationTest {

    @TestConfiguration
    static class InMemoryCacheConfig {
        @Bean
        @Primary
        public CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID);
        }
    }

    @Autowired
    private ProductVariationService productVariationService;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Autowired
    private CacheManager cacheManager;

    private UUID productId;
    private UUID variationId;
    private ProductVariation savedVariation;

    @BeforeEach
    void setUp() {
        // GH #61: the Postgres container/database is now genuinely shared across every
        // integration test class in this JVM fork (see BaseIntegrationTest), so this cleanup
        // has to be defensive about *any* class's leftover rows, not just this class's own -
        // in FK-safe order: inventory_deduction and inventory reference product_variation,
        // product_review and product_variation reference product.
        inventoryDeductionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productReviewRepository.deleteAll();
        productVariationRepository.deleteAll();
        productRepository.deleteAll();

        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .ownerId(UUID.randomUUID())
                .build());
        productId = product.getId();

        ProductVariationEntity variation = productVariationRepository.save(ProductVariationEntity.builder()
                .sku("TEST-SKU-001")
                .name("Test Variation")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(10)
                .isActive(true)
                .product(product)
                .build());
        variationId = variation.getId();
        // Mapped straight from the just-saved (not re-fetched) entity, so no lazy
        // collection is touched here.
        savedVariation = ProductVariationMapper.toDomain(variation);
    }

    @Test
    void update_shouldEvictCacheWithoutThrowing() {
        Cache cache = cacheManager.getCache(CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID);
        cache.put(productId, List.of(savedVariation)); // simulate a prior findByProductId(productId) call

        savedVariation.setPrice(BigDecimal.valueOf(149.99));

        assertThatCode(() -> productVariationService.update(savedVariation))
                .doesNotThrowAnyException();

        assertThat(cache.get(productId)).isNull();

        ProductVariationEntity persisted = productVariationRepository.findById(variationId).orElseThrow();
        assertThat(persisted.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(149.99));
    }

    @Test
    void delete_shouldEvictCacheWithoutThrowing() {
        Cache cache = cacheManager.getCache(CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID);
        cache.put(productId, List.of(savedVariation)); // simulate a prior findByProductId(productId) call

        assertThatCode(() -> productVariationService.delete(variationId))
                .doesNotThrowAnyException();

        assertThat(cache.get(productId)).isNull();
        assertThat(productVariationRepository.findById(variationId)).isEmpty();
    }

    @Test
    void delete_nonExistentId_shouldNotThrow() {
        assertThatCode(() -> productVariationService.delete(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}
