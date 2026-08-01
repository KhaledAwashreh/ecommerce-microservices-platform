package com.kawashreh.ecommerce.product_service.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.product_service.application.dto.ProductVariationDto;
import com.kawashreh.ecommerce.product_service.application.service.ProductVariationApplicationService;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import com.kawashreh.ecommerce.product_service.infastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for GH issue #14: ProductVariationService/Impl (create, update,
 * delete, find) were fully implemented but never wired into a controller, so there was no
 * HTTP path to set sku/price/stockQuantity on a variation. This verifies the new
 * ProductVariationController actually exposes those operations.
 */
// excludeFilters: this is a web-layer slice test, not concerned with the
// JwtAuthFilter added for GH #17. That filter needs a JwtService bean, which
// @WebMvcTest does not scan in, so it must be excluded rather than merely
// skipped via addFilters=false (the context would still fail to build it).
@WebMvcTest(controllers = ProductVariationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class ProductVariationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductVariationService productVariationService;

    @MockitoBean
    private ProductVariationApplicationService productVariationApplicationService;

    @Test
    void get_shouldReturnAllVariations() throws Exception {
        ProductVariation variation = ProductVariation.builder()
                .id(UUID.randomUUID())
                .sku("SKU-1")
                .price(BigDecimal.valueOf(9.99))
                .stockQuantity(5)
                .build();

        given(productVariationService.getAll()).willReturn(List.of(variation));

        mockMvc.perform(get("/api/v1/product-variation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("SKU-1"));
    }

    @Test
    void findById_shouldReturnVariation_whenFound() throws Exception {
        UUID variationId = UUID.randomUUID();
        ProductVariation variation = ProductVariation.builder()
                .id(variationId)
                .sku("SKU-2")
                .price(BigDecimal.valueOf(19.99))
                .stockQuantity(3)
                .build();

        given(productVariationService.find(variationId)).willReturn(variation);

        mockMvc.perform(get("/api/v1/product-variation/{productVariationId}", variationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variationId.toString()))
                .andExpect(jsonPath("$.sku").value("SKU-2"));
    }

    @Test
    void findById_shouldReturn404_whenNotFound() throws Exception {
        UUID variationId = UUID.randomUUID();
        given(productVariationService.find(variationId)).willReturn(null);

        mockMvc.perform(get("/api/v1/product-variation/{productVariationId}", variationId))
                .andExpect(status().isNotFound());
    }

    @Test
    void findByProductId_shouldReturnVariationsForThatProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).build();
        ProductVariation variation = ProductVariation.builder()
                .id(UUID.randomUUID())
                .sku("SKU-3")
                .price(BigDecimal.valueOf(29.99))
                .stockQuantity(10)
                .product(product)
                .build();

        given(productVariationService.findByProductId(productId)).willReturn(List.of(variation));

        mockMvc.perform(get("/api/v1/product-variation/product/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(productId.toString()));
    }

    @Test
    void create_shouldReturn201_withPersistedVariation() throws Exception {
        UUID productId = UUID.randomUUID();
        ProductVariationDto requestDto = ProductVariationDto.builder()
                .productId(productId)
                .sku("SKU-NEW")
                .name("New Variation")
                .price(BigDecimal.valueOf(49.99))
                .stockQuantity(20)
                .isActive(true)
                .build();

        Product product = Product.builder().id(productId).build();
        ProductVariation savedVariation = ProductVariation.builder()
                .sku("SKU-NEW")
                .name("New Variation")
                .price(BigDecimal.valueOf(49.99))
                .stockQuantity(20)
                .isActive(true)
                .product(product)
                .build();

        given(productVariationApplicationService.createVariation(any(ProductVariation.class), any(UUID.class)))
                .willReturn(savedVariation);

        mockMvc.perform(post("/api/v1/product-variation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-NEW"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.stockQuantity").value(20))
                .andExpect(jsonPath("$.productId").value(productId.toString()));
    }

    @Test
    void update_shouldReturn404_whenVariationNotFound() throws Exception {
        UUID variationId = UUID.randomUUID();
        given(productVariationService.find(variationId)).willReturn(null);

        ProductVariationDto requestDto = ProductVariationDto.builder()
                .sku("SKU-UPDATED")
                .price(BigDecimal.valueOf(59.99))
                .stockQuantity(7)
                .build();

        mockMvc.perform(put("/api/v1/product-variation/{productVariationId}", variationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_shouldReturn200_andUpdatePrice_whenFound() throws Exception {
        UUID variationId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        ProductVariation existing = ProductVariation.builder()
                .id(variationId)
                .sku("SKU-OLD")
                .price(BigDecimal.valueOf(10.00))
                .stockQuantity(1)
                .product(product)
                .build();

        given(productVariationService.find(variationId)).willReturn(existing);

        ProductVariationDto requestDto = ProductVariationDto.builder()
                .sku("SKU-UPDATED")
                .price(BigDecimal.valueOf(59.99))
                .stockQuantity(7)
                .build();

        mockMvc.perform(put("/api/v1/product-variation/{productVariationId}", variationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-UPDATED"))
                .andExpect(jsonPath("$.price").value(59.99));

        verify(productVariationService).update(any(ProductVariation.class));
    }

    // Regression test for GH #28: stockQuantity is synced from Inventory by
    // InventoryServiceImpl.deductStock/restoreStock exclusively; a PUT here must not let a
    // caller silently desync the two by supplying an arbitrary stockQuantity.
    @Test
    void update_shouldIgnoreClientSuppliedStockQuantity_andKeepExistingValue() throws Exception {
        UUID variationId = UUID.randomUUID();
        Product product = Product.builder().id(UUID.randomUUID()).build();
        ProductVariation existing = ProductVariation.builder()
                .id(variationId)
                .sku("SKU-OLD")
                .price(BigDecimal.valueOf(10.00))
                .stockQuantity(1)
                .product(product)
                .build();

        given(productVariationService.find(variationId)).willReturn(existing);

        ProductVariationDto requestDto = ProductVariationDto.builder()
                .sku("SKU-UPDATED")
                .price(BigDecimal.valueOf(59.99))
                .stockQuantity(999)
                .build();

        mockMvc.perform(put("/api/v1/product-variation/{productVariationId}", variationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(1));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        UUID variationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/product-variation/{productVariationId}", variationId))
                .andExpect(status().isNoContent());

        verify(productVariationService).delete(variationId);
    }
}
