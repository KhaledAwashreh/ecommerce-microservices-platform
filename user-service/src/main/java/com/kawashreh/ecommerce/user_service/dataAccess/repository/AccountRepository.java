package com.kawashreh.ecommerce.user_service.dataAccess;

import com.kawashreh.ecommerce.user_service.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
  public Account findByUserId(UUID userId);
}
