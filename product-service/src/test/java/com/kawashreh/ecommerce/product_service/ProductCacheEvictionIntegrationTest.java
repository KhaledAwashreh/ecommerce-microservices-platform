package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.constants.CacheConstants;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductReviewRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductReviewMapper;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for issue #35: several write paths in {@link ProductService} and
 * {@link ProductReviewService} never evicted the caches their corresponding reads
 * populate, leaving stale reads for up to the cache TTL (10 minutes) after a write.
 * <p>
 * The Redis-backed {@link CacheManager} is swapped for an in-memory one so this test only
 * depends on Postgres (via {@link BaseIntegrationTest}'s Testcontainers setup), not on a
 * running Redis instance — same approach as {@code ProductVariationServiceIntegrationTest}.
 */
@ActiveProfiles("test")
class ProductCacheEvictionIntegrationTest extends BaseIntegrationTest {

    @TestConfiguration
    static class InMemoryCacheConfig {
        @Bean
        @Primary
        public CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConstants.product_by_id,
                    CacheConstants.PRODUCT_REVIEW_BY_PRODUCT_ID
            );
        }
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductReviewService productReviewService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Autowired
    private CacheManager cacheManager;

    private ProductEntity productEntity;
    private Product product;

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

        productEntity = productRepository.save(ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .ownerId(UUID.randomUUID())
                .build());
        product = ProductMapper.toDomain(productEntity);
    }

    @Test
    void save_shouldEvictProductByIdCache() {
        Cache cache = cacheManager.getCache(CacheConstants.product_by_id);
        cache.put(product.getId(), product); // simulate a prior find(id) call

        Product updated = ProductMapper.toDomain(productEntity);
        updated.setName("Updated Name");

        productService.save(updated);

        assertThat(cache.get(product.getId())).isNull();

        ProductEntity persisted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Updated Name");
    }

    @Test
    void reviewSave_shouldEvictProductReviewByProductIdCache() {
        Cache cache = cacheManager.getCache(CacheConstants.PRODUCT_REVIEW_BY_PRODUCT_ID);
        cache.put(product.getId(), List.of()); // simulate a prior findByProductId(productId) call

        ProductReview review = ProductReview.builder()
                .userId(UUID.randomUUID())
                .product(product)
                .review("Great product")
                .stars(5)
                .build();

        productReviewService.save(review, product);

        assertThat(cache.get(product.getId())).isNull();
    }

    @Test
    void reviewUpdate_shouldEvictProductReviewByProductIdCache() {
        ProductReviewEntity reviewEntity = productReviewRepository.save(ProductReviewEntity.builder()
                .userId(UUID.randomUUID())
                .product(productEntity)
                .review("Initial review")
                .stars(3)
                .build());
        ProductReview review = ProductReviewMapper.toDomain(reviewEntity);

        Cache cache = cacheManager.getCache(CacheConstants.PRODUCT_REVIEW_BY_PRODUCT_ID);
        cache.put(product.getId(), List.of(review)); // simulate a prior findByProductId(productId) call

        review.setReview("Updated review");
        productReviewService.update(review);

        assertThat(cache.get(product.getId())).isNull();

        ProductReviewEntity persisted = productReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(persisted.getReview()).isEqualTo("Updated review");
    }

    @Test
    void reviewDelete_shouldEvictProductReviewByProductIdCache() {
        ProductReviewEntity reviewEntity = productReviewRepository.save(ProductReviewEntity.builder()
                .userId(UUID.randomUUID())
                .product(productEntity)
                .review("Initial review")
                .stars(3)
                .build());

        Cache cache = cacheManager.getCache(CacheConstants.PRODUCT_REVIEW_BY_PRODUCT_ID);
        cache.put(product.getId(), List.of(ProductReviewMapper.toDomain(reviewEntity))); // simulate a prior findByProductId(productId) call

        productReviewService.delete(reviewEntity.getId());

        assertThat(cache.get(product.getId())).isNull();
        assertThat(productReviewRepository.findById(reviewEntity.getId())).isEmpty();
    }
}
