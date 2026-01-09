package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDto;
import com.kawashreh.ecommerce.product_service.application.mapper.ProductReviewHttpMapper;
import com.kawashreh.ecommerce.product_service.application.service.ReviewApplicationService;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/productReview")
public class ProductReviewController {


    private final ProductReviewService service;
    private final ReviewApplicationService reviewApplicationService;

    public ProductReviewController(ProductReviewService productReviewService, ReviewApplicationService reviewApplicationService) {
        this.service = productReviewService;
        this.reviewApplicationService = reviewApplicationService;
    }

    @GetMapping()
    public ResponseEntity<List<ProductReviewDto>> get() {
        List<ProductReviewDto> dtos = service.getAll()
                .stream()
                .map(ProductReviewHttpMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<List<ProductReviewDto>> getReviewsForProduct(@PathVariable UUID productId) {
        List<ProductReviewDto> dtos = service.findByProductId(productId)
                .stream()
                .map(ProductReviewHttpMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);

    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ProductReviewDto> findById(@PathVariable UUID reviewId) {
        ProductReview review = service.find(reviewId);
        if (review == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ProductReviewHttpMapper.toDto(review));
    }

    @PostMapping
    public ResponseEntity<ProductReviewDto> create(@RequestBody ProductReviewDto dto) {

        ProductReview review = reviewApplicationService.createReview(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(ProductReviewHttpMapper.toDto(review));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId) {
        service.delete(reviewId);
        return ResponseEntity.noContent().build();
    }

}

