package com.kawashreh.ecommerce.user_service.dataAccess.repository;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AccountEntity;
import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    public AccountEntity findByUserId(UUID userId);

    List<AccountEntity> findByUserIdIn(List<UUID> userIds);
}
