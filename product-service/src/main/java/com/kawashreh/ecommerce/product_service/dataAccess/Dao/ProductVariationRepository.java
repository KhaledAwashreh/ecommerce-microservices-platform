package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductVariationRepository extends JpaRepository<ProductVariationEntity, UUID> {

    List<ProductVariationEntity> findByProductId(UUID productId);
    void deleteByProductId(UUID productId);
    long countByProductId(UUID productId);

    // GH #28: keeps the duplicate ProductVariationEntity.stockQuantity mirror in sync with
    // Inventory.quantity (authoritative) whenever InventoryServiceImpl deducts/restores stock.
    @Modifying
    @Query("UPDATE ProductVariationEntity v SET v.stockQuantity = :stockQuantity WHERE v.id = :productVariationId")
    int updateStockQuantity(UUID productVariationId, int stockQuantity);
}
