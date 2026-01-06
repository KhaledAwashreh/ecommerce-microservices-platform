package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDTO;
import com.kawashreh.ecommerce.product_service.application.service.ReviewApplicationService;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productReview")
public class ProductReviewController {


    private final ProductReviewService service;
    private final ReviewApplicationService reviewApplicationService;

    public ProductReviewController(ProductReviewService productReviewService, ReviewApplicationService reviewApplicationService) {
        this.service = productReviewService;
        this.reviewApplicationService = reviewApplicationService;
    }

    @GetMapping("{productId}")
    public ResponseEntity<List<ProductReview>> getReviewsForProduct(@PathVariable UUID productId) {
        List<ProductReview> reviews = service.findByProductId(productId);
        return ResponseEntity.ok(reviews);

    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ProductReview> findById(@PathVariable UUID reviewId) {
        ProductReview review = service.find(reviewId);
        return ResponseEntity.ok(review);
    }

    @PostMapping
    public ResponseEntity<ProductReview> create(@RequestBody ProductReviewDTO dto) {

        ProductReview review = reviewApplicationService.createReview(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId) {
        service.delete(reviewId);
        return ResponseEntity.noContent().build();
    }

}
