package com.kawashreh.ecommerce.user_service.dataAccess.repository;

import com.kawashreh.ecommerce.user_service.dataAccess.entity.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {
}
