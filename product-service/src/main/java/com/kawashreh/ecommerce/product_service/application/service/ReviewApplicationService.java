package com.kawashreh.ecommerce.product_service.application.service;


import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDto;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import com.kawashreh.ecommerce.product_service.infastructure.http.client.UserServiceClient;
import com.kawashreh.ecommerce.product_service.infastructure.http.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ReviewApplicationService {

    private final ProductReviewService productReviewService;
    private final UserServiceClient userServiceClient;
    private final ProductService productService;

    public ReviewApplicationService(ProductReviewService productReviewService, UserServiceClient userServiceClient, ProductService productService) {
        this.productReviewService = productReviewService;
        this.userServiceClient = userServiceClient;
        this.productService = productService;
    }

    public ProductReview createReview(ProductReviewDto dto) {

        Product product = productService.find(dto.getProductId());
        if (Objects.isNull(product)) {
            // GH #41: throw instead of returning null so the controller no longer responds
            // 201 Created with an empty body on failure.
            throw new NoSuchElementException("Product not found: " + dto.getProductId());
        }

        UserDto user = userServiceClient.retrieveUser(dto.getUserId());
        if (Objects.isNull(user)) {
            throw new NoSuchElementException("User not found: " + dto.getUserId());
        }

        if (product.getOwnerId().equals(user.getId())) {
            throw new IllegalArgumentException("Cannot review your own product: " + dto.getProductId());
        }

        ProductReview review = ProductReview.builder()
                .product(product)
                .userId(dto.getUserId())
                .review(dto.getReview())
                .stars(dto.getStars())
                .build();

        return productReviewService.save(review, product);
    }
}
