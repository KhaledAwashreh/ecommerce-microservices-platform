package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryDeductionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryDeductionRepository extends JpaRepository<InventoryDeductionEntity, UUID> {

    Optional<InventoryDeductionEntity> findByOrderItemId(UUID orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM InventoryDeductionEntity d WHERE d.orderItemId = :orderItemId")
    Optional<InventoryDeductionEntity> findByOrderItemIdWithLock(UUID orderItemId);
}
