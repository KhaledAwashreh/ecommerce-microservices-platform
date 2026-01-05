package com.kawashreh.ecommerce.product_service.dataAccess.Dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    public Optional<CategoryEntity> findByName(String name);
}
