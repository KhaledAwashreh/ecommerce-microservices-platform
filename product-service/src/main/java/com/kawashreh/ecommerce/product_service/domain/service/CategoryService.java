package com.kawashreh.ecommerce.product_service.domain.service;

import com.kawashreh.ecommerce.product_service.domain.model.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    public List<Category> getAll();

    public Category find(UUID id);

    public Category save(Category category);

    public Category update(Category category);

    public void delete(UUID id);

    public void findByName(String name);
}
