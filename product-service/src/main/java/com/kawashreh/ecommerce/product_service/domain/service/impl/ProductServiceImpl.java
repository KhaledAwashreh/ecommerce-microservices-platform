package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.Dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;

    public ProductServiceImpl(ProductRepository repository) {
        this.repository = repository;
    }
    @Override
    public List<Product> getAll() {

        return repository.findAll();
    }

    @Override
    public Product find(UUID id) {

return repository.findById(id).orElse(null);    }

    @Override
    public void save(Product product) {

        repository.save(product);
    }

    @Override
    public void update(Product product) {

        repository.save(product);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
