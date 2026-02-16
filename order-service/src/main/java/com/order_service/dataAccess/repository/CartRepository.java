package com.order_service.dataAccess.repository;

import com.order_service.domain.enums.CartStatus;
import com.order_service.dataAccess.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    Optional<CartEntity> findById(UUID id);

    Optional<CartEntity> findByUserId(UUID userId);

    Optional<CartEntity> findBySessionId(UUID sessionId);

    List<CartEntity> findByStatus(CartStatus status);

    @Query("""
        SELECT c FROM CartEntity c
        WHERE c.userId = :userId
        AND c.status = :status
    """)
    Optional<CartEntity> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") CartStatus status);

    @Query("""
        SELECT c FROM CartEntity c
        WHERE c.sessionId = :sessionId
        AND c.status = :status
    """)
    Optional<CartEntity> findBySessionIdAndStatus(@Param("sessionId") UUID sessionId, @Param("status") CartStatus status);
}
