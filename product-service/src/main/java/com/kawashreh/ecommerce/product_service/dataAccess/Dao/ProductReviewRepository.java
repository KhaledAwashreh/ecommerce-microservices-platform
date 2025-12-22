package com.kawashreh.ecommerce.product_service.dataAccess.Dao;

import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {
}
