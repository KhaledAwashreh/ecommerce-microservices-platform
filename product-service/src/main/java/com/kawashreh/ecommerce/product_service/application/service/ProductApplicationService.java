package com.kawashreh.ecommerce.product_service.application.service;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import com.kawashreh.ecommerce.product_service.infastructure.http.client.UserServiceClient;
import com.kawashreh.ecommerce.product_service.infastructure.http.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ProductApplicationService {


    private final UserServiceClient userServiceClient;
    private final ProductService productService;

    public ProductApplicationService(UserServiceClient userServiceClient, ProductService productService) {
        this.userServiceClient = userServiceClient;
        this.productService = productService;
    }

    public Product createProduct(Product product) {

        UserDto user = userServiceClient.retrieveUser(product.getOwnerId());
        if (Objects.isNull(user)) {
            // GH #41: throw instead of returning null so the controller no longer responds
            // 201 Created with an empty body on failure.
            throw new NoSuchElementException("User not found: " + product.getOwnerId());
        }
        productService.save(product);
        return product;
    }
}
