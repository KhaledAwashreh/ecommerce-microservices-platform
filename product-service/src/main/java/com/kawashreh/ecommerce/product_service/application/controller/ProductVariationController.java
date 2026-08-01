package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.ProductVariationDto;
import com.kawashreh.ecommerce.product_service.application.mapper.ProductVariationHttpMapper;
import com.kawashreh.ecommerce.product_service.application.service.ProductVariationApplicationService;
import com.kawashreh.ecommerce.product_service.constants.ApiPaths;
import com.kawashreh.ecommerce.product_service.domain.model.ProductVariation;
import com.kawashreh.ecommerce.product_service.domain.service.ProductVariationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(ApiPaths.PRODUCT_VARIATION_BASE)
public class ProductVariationController {

    private final ProductVariationService service;
    private final ProductVariationApplicationService productVariationApplicationService;

    public ProductVariationController(ProductVariationService productVariationService,
                                       ProductVariationApplicationService productVariationApplicationService) {
        this.service = productVariationService;
        this.productVariationApplicationService = productVariationApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<ProductVariationDto>> get() {
        List<ProductVariationDto> dtos = service.getAll()
                .stream()
                .map(ProductVariationHttpMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping(ApiPaths.PRODUCT_VARIATION_BY_ID)
    public ResponseEntity<ProductVariationDto> findById(@PathVariable UUID productVariationId) {
        ProductVariation variation = service.find(productVariationId);
        if (variation == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ProductVariationHttpMapper.toDto(variation));
    }

    @GetMapping(ApiPaths.PRODUCT_VARIATION_BY_PRODUCT)
    public ResponseEntity<List<ProductVariationDto>> findByProductId(@PathVariable UUID productId) {
        List<ProductVariationDto> dtos = service.findByProductId(productId)
                .stream()
                .map(ProductVariationHttpMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ProductVariationDto> create(@RequestBody ProductVariationDto dto) {
        ProductVariation variation = ProductVariationHttpMapper.toDomain(dto);
        ProductVariation result = productVariationApplicationService.createVariation(variation, dto.getProductId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductVariationHttpMapper.toDto(result));
    }

    @PutMapping(ApiPaths.PRODUCT_VARIATION_BY_ID)
    public ResponseEntity<ProductVariationDto> update(@PathVariable UUID productVariationId, @RequestBody ProductVariationDto dto) {
        ProductVariation existing = service.find(productVariationId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        dto.setId(productVariationId);
        ProductVariation variation = ProductVariationHttpMapper.toDomain(dto);
        variation.setProduct(existing.getProduct());
        // GH #28: Inventory.quantity is authoritative for stock; deductStock/restoreStock
        // are the only paths allowed to change stockQuantity (kept in sync with Inventory).
        // Discard any client-supplied stockQuantity here rather than let a PUT silently
        // desync the two.
        variation.setStockQuantity(existing.getStockQuantity());

        service.update(variation);
        return ResponseEntity.ok(ProductVariationHttpMapper.toDto(variation));
    }

    @DeleteMapping(ApiPaths.PRODUCT_VARIATION_BY_ID)
    public ResponseEntity<Void> delete(@PathVariable UUID productVariationId) {
        service.delete(productVariationId);
        return ResponseEntity.noContent().build();
    }
}
