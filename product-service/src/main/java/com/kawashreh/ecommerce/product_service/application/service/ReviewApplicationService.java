package com.kawashreh.ecommerce.product_service.application.service;


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
        UserDto user = userServiceClient.retrieveUser(dto.getUserId());
        ProductReview review = null;

        if (Objects.nonNull(user) && Objects.nonNull(product)) {
             review = ProductReview.builder()
                    .product(product)
                    .userId(dto.getUserId())
                    .review(dto.getReview())
                    .stars(dto.getStars())
                    .build();
            productReviewService.save(review);
        }
        return review;
    }
}
