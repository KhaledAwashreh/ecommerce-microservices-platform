package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.ProductDto;
import com.kawashreh.ecommerce.product_service.application.mapper.ProductHttpMapper;
import com.kawashreh.ecommerce.product_service.application.service.ProductApplicationService;
import com.kawashreh.ecommerce.product_service.domain.model.Product;
import com.kawashreh.ecommerce.product_service.domain.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/product")
public class ProductController {

    private final ProductService service;
    private final ProductApplicationService productApplicationService;

    public ProductController(ProductService productService, ProductApplicationService productApplicationService) {
        this.service = productService;
        this.productApplicationService = productApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<ProductDto>> get() {
        List<ProductDto> dtos = service.getAll()
                .stream()
                .map(ProductHttpMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDto> findById(@PathVariable UUID productId) {
        Product product = service.find(productId);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ProductHttpMapper.toDto(product));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@RequestBody ProductDto productDto) {
        Product product = ProductHttpMapper.toDomain(productDto);
        Product result = productApplicationService.createProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductHttpMapper.toDto(result));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable UUID productId) {
        service.delete(productId);
        return ResponseEntity.noContent().build();
    }
}
