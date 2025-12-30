package com.kawashreh.ecommerce.user_service.dataAccess;

import com.kawashreh.ecommerce.user_service.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    public Optional<User> findByUsername(String username);

    public Optional<User> findByEmail(String email);

}
