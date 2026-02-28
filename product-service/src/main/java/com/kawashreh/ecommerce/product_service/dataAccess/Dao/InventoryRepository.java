package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {

    Optional<InventoryEntity> findByProductVariationId(UUID productVariationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryEntity i WHERE i.productVariation.id = :productVariationId")
    Optional<InventoryEntity> findByProductVariationIdWithLock(UUID productVariationId);

    @Modifying
    @Query("UPDATE InventoryEntity i SET i.quantity = i.quantity - :quantity WHERE i.productVariation.id = :productVariationId AND i.quantity >= :quantity")
    int deductQuantity(UUID productVariationId, int quantity);

    @Modifying
    @Query("UPDATE InventoryEntity i SET i.quantity = i.quantity + :quantity WHERE i.productVariation.id = :productVariationId")
    int restoreQuantity(UUID productVariationId, int quantity);
}
