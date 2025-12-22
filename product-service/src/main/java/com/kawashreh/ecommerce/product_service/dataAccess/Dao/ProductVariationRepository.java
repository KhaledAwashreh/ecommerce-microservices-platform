package com.kawashreh.ecommerce.product_service.dataAccess.Dao;

import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariationRepository extends JpaRepository<ProductVariation, UUID> {

    List<ProductVariation> findByProductId(UUID productId);
    Page<ProductVariation> findByProductId(UUID productId, Pageable pageable);
    long countByProductId(UUID productId);
}
