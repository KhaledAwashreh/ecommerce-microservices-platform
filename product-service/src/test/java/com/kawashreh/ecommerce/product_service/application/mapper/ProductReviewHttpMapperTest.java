package com.kawashreh.ecommerce.product_service.application.mapper;

import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDto;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for GH #26: toDto used to read createdAt from the *product*
 * (review.getProduct().getCreatedAt()) instead of the review's own createdAt, never
 * mapped updatedAt at all, and dereferenced review.getProduct() unguarded.
 */
class ProductReviewHttpMapperTest {

    @Test
    void toDto_shouldUseReviewsOwnTimestamps_notTheProducts() {
        Instant productCreatedAt = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant reviewCreatedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant reviewUpdatedAt = Instant.now();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .createdAt(productCreatedAt)
                .build();

        ProductReview review = ProductReview.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .product(product)
                .review("Great product")
                .stars(5)
                .createdAt(reviewCreatedAt)
                .updatedAt(reviewUpdatedAt)
                .build();

        ProductReviewDto dto = ProductReviewHttpMapper.toDto(review);

        assertThat(dto.getCreatedAt()).isEqualTo(reviewCreatedAt);
        assertThat(dto.getCreatedAt()).isNotEqualTo(productCreatedAt);
        assertThat(dto.getUpdatedAt()).isEqualTo(reviewUpdatedAt);
    }

    @Test
    void toDto_shouldNotThrow_whenProductIsNull() {
        ProductReview review = ProductReview.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .product(null)
                .review("Orphaned review")
                .stars(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ProductReviewDto dto = ProductReviewHttpMapper.toDto(review);

        assertThat(dto.getProductId()).isNull();
        assertThat(dto.getCreatedAt()).isEqualTo(review.getCreatedAt());
    }
}
