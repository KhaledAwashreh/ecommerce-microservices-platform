package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.application.dto.CategoryDto;
import com.kawashreh.ecommerce.product_service.application.mapper.CategoryHttpMapper;
import com.kawashreh.ecommerce.product_service.domain.model.Category;
import com.kawashreh.ecommerce.product_service.domain.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CategoryDto>> findAll() {
        List<CategoryDto> dtos = service.getAll()
                .stream()
                .map(CategoryHttpMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryDto> findById(@PathVariable UUID categoryId) {
        Category category = service.find(categoryId);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CategoryHttpMapper.toDto(category));
    }

    @GetMapping("/name/{categoryName}")
    public ResponseEntity<CategoryDto> findByName(@PathVariable String categoryName) {
        Category category = service.findByName(categoryName);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CategoryHttpMapper.toDto(category));
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryDto categoryDto) {
        Category category = CategoryHttpMapper.toDomain(categoryDto);
        service.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryHttpMapper.toDto(category));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId) {
        service.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}