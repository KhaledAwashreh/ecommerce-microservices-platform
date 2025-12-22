package com.kawashreh.ecommerce.product_service.domain.service;


import com.kawashreh.ecommerce.product_service.domain.model.Product;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    public List<Product> getAll();

    public Product find(UUID id);

    public Product save(Product product);

    public Product update(Product product);

    public void delete(UUID id);

    public void findByName(String name);
}
