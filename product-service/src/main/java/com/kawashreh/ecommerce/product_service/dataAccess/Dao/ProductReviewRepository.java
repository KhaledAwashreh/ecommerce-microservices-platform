package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReviewEntity, UUID> {

    List<ProductReviewEntity> findByProductId(UUID productId);
    Page<ProductReviewEntity> findByProductId(UUID productId, Pageable pageable);
    List<ProductReviewEntity> findByUserId(UUID userId);
    long countByProductId(UUID productId);
}
