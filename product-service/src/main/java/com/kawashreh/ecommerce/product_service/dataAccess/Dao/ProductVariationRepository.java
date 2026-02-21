package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariationRepository extends JpaRepository<ProductVariationEntity, UUID> {

    List<ProductVariationEntity> findByProductId(UUID productId);
    Page<ProductVariationEntity> findByProductId(UUID productId, Pageable pageable);
    void deleteByProductId(UUID productId);
    long countByProductId(UUID productId);
}
