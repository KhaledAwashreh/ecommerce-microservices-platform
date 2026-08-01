package com.kawashreh.ecommerce.product_service.application.service;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.product_service.application.dto.ProductReviewDto;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductReview;
import com.kawashreh.ecommerce.product_service.domain.service.ProductReviewService;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import com.kawashreh.ecommerce.product_service.infastructure.http.client.UserServiceClient;
import com.kawashreh.ecommerce.product_service.infastructure.http.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for GH #41: createReview used to return null (silently) on unknown
 * user, unknown product, or a self-review attempt, which let the controller respond 201
 * Created with an empty body. Each failure must now throw so a handler can map it to a
 * real 4xx.
 */
@ExtendWith(MockitoExtension.class)
class ReviewApplicationServiceTest {

    @Mock
    private ProductReviewService productReviewService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductService productService;

    private ReviewApplicationService applicationService;

    @Test
    void createReview_shouldThrowNotFound_whenProductDoesNotExist() {
        applicationService = new ReviewApplicationService(productReviewService, userServiceClient, productService);
        UUID productId = UUID.randomUUID();
        ProductReviewDto dto = ProductReviewDto.builder().productId(productId).userId(UUID.randomUUID()).build();

        given(productService.find(productId)).willReturn(null);

        assertThatThrownBy(() -> applicationService.createReview(dto))
                .isInstanceOf(NoSuchElementException.class);

        verify(productReviewService, never()).save(any(), any());
    }

    @Test
    void createReview_shouldThrowNotFound_whenUserDoesNotExist() {
        applicationService = new ReviewApplicationService(productReviewService, userServiceClient, productService);
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProductReviewDto dto = ProductReviewDto.builder().productId(productId).userId(userId).build();
        Product product = Product.builder().id(productId).ownerId(UUID.randomUUID()).build();

        given(productService.find(productId)).willReturn(product);
        given(userServiceClient.retrieveUser(userId)).willReturn(null);

        assertThatThrownBy(() -> applicationService.createReview(dto))
                .isInstanceOf(NoSuchElementException.class);

        verify(productReviewService, never()).save(any(), any());
    }

    @Test
    void createReview_shouldThrowIllegalArgument_whenReviewingOwnProduct() {
        applicationService = new ReviewApplicationService(productReviewService, userServiceClient, productService);
        UUID productId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ProductReviewDto dto = ProductReviewDto.builder().productId(productId).userId(ownerId).build();
        Product product = Product.builder().id(productId).ownerId(ownerId).build();

        given(productService.find(productId)).willReturn(product);
        given(userServiceClient.retrieveUser(ownerId)).willReturn(UserDto.builder().id(ownerId).build());

        assertThatThrownBy(() -> applicationService.createReview(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productReviewService, never()).save(any(), any());
    }

    @Test
    void createReview_shouldSaveAndReturnReview_whenValid() {
        applicationService = new ReviewApplicationService(productReviewService, userServiceClient, productService);
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ProductReviewDto dto = ProductReviewDto.builder()
                .productId(productId)
                .userId(userId)
                .review("Nice")
                .stars(5)
                .build();
        Product product = Product.builder().id(productId).ownerId(ownerId).build();

        given(productService.find(productId)).willReturn(product);
        given(userServiceClient.retrieveUser(userId)).willReturn(UserDto.builder().id(userId).build());

        ProductReview result = applicationService.createReview(dto);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        verify(productReviewService).save(result, product);
    }
}
