package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.CategoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.mapper.CategoryMapper;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.CategoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Category;
import com.kawashreh.ecommerce.product_service.domain.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;

    public CategoryServiceImpl(CategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Category> getAll() {
        List<CategoryEntity> entities = repository.findAll();
        return CategoryMapper.toDomainList(entities);
    }

    @Override
    public Category find(UUID id) {
        return repository.findById(id).map(CategoryMapper::toDomain).orElse(null);
    }

    @Override
    public void save(Category category) {
        repository.save(CategoryMapper.toEntity(category));
    }

    @Override
    public void saveBatch(List<Category> categories) {
        repository.saveAll(CategoryMapper.toEntityList(categories));
    }

    @Override
    public void update(Category category) {
        repository.save(CategoryMapper.toEntity(category));
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public Category findByName(String name) {
        return repository.findByName(name).map(CategoryMapper::toDomain).orElse(null);
    }
}
