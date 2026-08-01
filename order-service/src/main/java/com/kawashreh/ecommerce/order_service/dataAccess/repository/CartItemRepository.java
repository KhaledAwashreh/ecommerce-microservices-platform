package com.kawashreh.ecommerce.order_service.dataAccess.repository;

import com.kawashreh.ecommerce.order_service.dataAccess.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    List<CartItemEntity> findByCartId(UUID cartId);

    Optional<CartItemEntity> findByIdAndCartId(UUID id, UUID cartId);

    void deleteByCartId(UUID cartId);
}
