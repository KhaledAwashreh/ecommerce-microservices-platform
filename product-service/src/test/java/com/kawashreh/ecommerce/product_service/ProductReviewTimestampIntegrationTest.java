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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the "compounding" part of GH #26: ProductReviewEntity had no
 * @CreationTimestamp/@UpdateTimestamp, so created_at/updated_at were always persisted as
 * NULL regardless of the HTTP mapper fix, and the entity<->domain ProductReviewMapper
 * never carried the timestamps across layers either.
 */
@ActiveProfiles("test")
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

        ProductReviewEntity saved = productReviewRepository.save(entity);

        assertThat(saved.getCreatedAt()).isNotNull();

        ProductReview domain = ProductReviewMapper.toDomain(saved);
        assertThat(domain.getCreatedAt()).isNotNull();
        assertThat(domain.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
