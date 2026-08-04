package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductReviewRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.ProductReviewMapper;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the "compounding" part of GH #26: ProductReviewEntity had no
 * @CreationTimestamp/@UpdateTimestamp, so created_at/updated_at were always persisted as
 * NULL regardless of the HTTP mapper fix, and the entity<->domain ProductReviewMapper
 * never carried the timestamps across layers either.
 * <p>
 * {@code @Transactional} (GH #61): the Postgres container/database is now genuinely shared
 * across every integration test class in this JVM fork (see {@link BaseIntegrationTest}), so
 * the product+review this test saves must not outlive it - without a rollback, it's an
 * orphaned row visible to every later test class, and specifically breaks
 * {@code InventoryServiceIntegrationTest#setUp} (which does a plain
 * {@code productRepository.deleteAll()}) with a FK violation from the leftover
 * {@code product_review} row. Same pattern {@link InventoryDeductionRepositoryTest} already
 * uses for the same reason. Note this test now uses {@code saveAndFlush} rather than
 * {@code save}: Hibernate's {@code @CreationTimestamp} generator only runs at actual flush
 * time, which {@code save()} no longer forces once it's joining this test's own open
 * transaction instead of committing its own - {@code saveAndFlush} keeps the assertion below
 * meaningful while the surrounding {@code @Transactional} still rolls the insert back after.
 */
@ActiveProfiles("test")
@Transactional
class ProductReviewTimestampIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savingAReview_shouldPopulateCreatedAtAndSurfaceThroughTheDomainMapper() {
        ProductEntity product = ProductEntity.builder()
                .name("Widget")
                .description("A widget")
                .ownerId(UUID.randomUUID())
                .build();
        product = productRepository.save(product);

        ProductReviewEntity entity = ProductReviewEntity.builder()
                .userId(UUID.randomUUID())
                .product(product)
                .review("Solid")
                .stars(4)
                .build();

        ProductReviewEntity saved = productReviewRepository.saveAndFlush(entity);

        assertThat(saved.getCreatedAt()).isNotNull();

        ProductReview domain = ProductReviewMapper.toDomain(saved);
        assertThat(domain.getCreatedAt()).isNotNull();
        assertThat(domain.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
