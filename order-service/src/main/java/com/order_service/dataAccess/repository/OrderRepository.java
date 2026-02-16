package com.order_service.dataAccess.repository;

import com.order_service.dataAccess.entity.OrderEntity;
import com.order_service.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findById(UUID id);

    List<OrderEntity> findByBuyer(UUID buyerId);

    List<OrderEntity> findBySeller(UUID sellerId);

    List<OrderEntity> findByStoreId(UUID storeId);

    List<OrderEntity> findByStatus(OrderStatus status);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.buyer = :buyerId
        AND o.storeId = :storeId
    """)
    List<OrderEntity> findByBuyerAndStoreId(@Param("buyerId") UUID buyerId, @Param("storeId") UUID storeId);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.seller = :sellerId
        AND o.storeId = :storeId
    """)
    List<OrderEntity> findBySellerAndStoreId(@Param("sellerId") UUID sellerId, @Param("storeId") UUID storeId);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.buyer = :buyerId
        AND o.status = :status
    """)
    List<OrderEntity> findByBuyerAndStatus(@Param("buyerId") UUID buyerId, @Param("status") OrderStatus status);
}

