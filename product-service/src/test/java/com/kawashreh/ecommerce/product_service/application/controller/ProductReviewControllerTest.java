package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.service.ReviewApplicationService;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for GH issue #1: GET /{productId} (list reviews for a product) and
 * GET /{reviewId} (single review) used to be mapped as two identical single-variable
 * path templates on the same controller/verb, which made Spring throw
 * "IllegalStateException: Ambiguous handler methods mapped" on every request. The two
 * endpoints must now resolve to distinct, unambiguous paths.
 */
@WebMvcTest(ProductReviewController.class)
class ProductReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductReviewService productReviewService;

    @MockitoBean
    private ReviewApplicationService reviewApplicationService;

    @Test
    void getReviewsForProduct_shouldReturnReviewsForThatProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .createdAt(Instant.now())
                .build();
        ProductReview review = ProductReview.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .product(product)
                .review("Great product")
                .stars(5)
                .build();

        given(productReviewService.findByProductId(productId)).willReturn(List.of(review));

        mockMvc.perform(get("/api/v1/productReview/product/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$[0].review").value("Great product"));
    }

    @Test
    void findById_shouldReturnReview_whenFound() throws Exception {
        UUID reviewId = UUID.randomUUID();
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
        ProductReview review = ProductReview.builder()
                .id(reviewId)
                .userId(UUID.randomUUID())
                .product(product)
                .review("Solid")
                .stars(4)
                .build();

        given(productReviewService.find(reviewId)).willReturn(review);

        mockMvc.perform(get("/api/v1/productReview/{reviewId}", reviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId.toString()))
                .andExpect(jsonPath("$.review").value("Solid"));
    }

    @Test
    void findById_shouldReturn404_whenReviewNotFound() throws Exception {
        UUID reviewId = UUID.randomUUID();
        given(productReviewService.find(reviewId)).willReturn(null);

        mockMvc.perform(get("/api/v1/productReview/{reviewId}", reviewId))
                .andExpect(status().isNotFound());
    }
}
