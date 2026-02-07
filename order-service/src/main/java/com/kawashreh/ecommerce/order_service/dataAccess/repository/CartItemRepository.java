package com.kawashreh.ecommerce.order_service.dataAccess.repository;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    List<CartItemEntity> findByCartId(UUID cartId);

    Optional<CartItemEntity> findByIdAndCartId(UUID id, UUID cartId);

    void deleteByCartId(UUID cartId);

    @Query("""
        SELECT ci FROM CartItemEntity ci
        WHERE ci.cart.id = :cartId
        AND ci.storeId = :storeId
    """)
    List<CartItemEntity> findByCartIdAndStoreId(@Param("cartId") UUID cartId, @Param("storeId") UUID storeId);

    @Query("""
        SELECT ci FROM CartItemEntity ci
        WHERE ci.cart.id = :cartId
        AND ci.productId = :productId
    """)
    Optional<CartItemEntity> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);
}
