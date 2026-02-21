package com.kawashreh.ecommerce.user_service.dataAccess.repository;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    public Optional<UserEntity> findByUsername(String username);

    public Optional<UserEntity> findByEmail(String email);

    @Query("""
        select u from UserEntity u
        left join fetch u.account
        where u.username = :username
        """)
    Optional<UserEntity> findByUsernameWithAccount(String username);
}
