package com.kawashreh.ecommerce.product_service.application.Controller;

import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/productReview")
public class ProductReviewController {


    private final ProductReviewService service;

    public ProductReviewController(ProductReviewService productReviewService) {
        this.service = productReviewService;
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
    public ResponseEntity<ProductReview> create(@RequestBody ProductReview review) {
        service.save(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }


    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId) {
        service.delete(reviewId);
        return ResponseEntity.noContent().build();
    }

}
