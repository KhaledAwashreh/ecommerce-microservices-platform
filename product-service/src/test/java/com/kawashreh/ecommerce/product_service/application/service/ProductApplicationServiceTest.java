package com.kawashreh.ecommerce.product_service.application.service;

import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import com.kawashreh.ecommerce.product_service.infastructure.http.client.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for GH #41: createProduct used to return null (silently) when the
 * owning user could not be found, which let the controller respond 201 Created with an
 * empty body. It must now throw so a handler can map it to a real 4xx.
 */
@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductService productService;

    private ProductApplicationService applicationService;

    @Test
    void createProduct_shouldThrow_whenOwningUserDoesNotExist() {
        applicationService = new ProductApplicationService(userServiceClient, productService);
        UUID ownerId = UUID.randomUUID();
        Product product = Product.builder().ownerId(ownerId).build();

        given(userServiceClient.retrieveUser(ownerId)).willReturn(null);

        assertThatThrownBy(() -> applicationService.createProduct(product))
                .isInstanceOf(NoSuchElementException.class);

        verify(productService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createProduct_shouldSaveAndReturnProduct_whenUserExists() {
        applicationService = new ProductApplicationService(userServiceClient, productService);
        UUID ownerId = UUID.randomUUID();
        Product product = Product.builder().ownerId(ownerId).build();

        given(userServiceClient.retrieveUser(ownerId))
                .willReturn(com.kawashreh.ecommerce.product_service.infastructure.http.dto.UserDto.builder()
                        .id(ownerId)
                        .build());

        Product result = applicationService.createProduct(product);

        assertThat(result).isEqualTo(product);
        verify(productService).save(product);
    }
}
