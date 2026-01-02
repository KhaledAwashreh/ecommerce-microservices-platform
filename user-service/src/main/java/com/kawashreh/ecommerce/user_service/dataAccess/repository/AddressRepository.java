package com.kawashreh.ecommerce.user_service.dataAccess.repository;

import com.kawashreh.ecommerce.user_service.domain.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
}
