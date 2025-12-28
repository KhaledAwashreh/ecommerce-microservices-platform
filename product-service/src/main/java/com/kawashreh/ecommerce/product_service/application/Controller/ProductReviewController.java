package com.kawashreh.ecommerce.product_service.application.Controller;

import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDTO;
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
    private final ProductService productService;

    public ProductReviewController(ProductReviewService productReviewService, ProductService productService) {
        this.service = productReviewService;
        this.productService = productService;
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
        Product product = productService.find(dto.getProductId());
        ProductReview review = ProductReview.builder()
                .product(product)
                .userId(dto.getUserId())
                .review(dto.getReview())
                .stars(dto.getStars())
                .build();
        service.save(review);

        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }


    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId) {
        service.delete(reviewId);
        return ResponseEntity.noContent().build();
    }

}
