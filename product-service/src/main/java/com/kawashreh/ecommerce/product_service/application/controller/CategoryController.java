package com.kawashreh.ecommerce.product_service.application.controller;

import com.kawashreh.ecommerce.product_service.domain.model.Category;
import com.kawashreh.ecommerce.product_service.domain.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Category>> findAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<Category> findById(@PathVariable UUID categoryId) {
        Category category = service.find(categoryId);
        return ResponseEntity.ok(category);
    }

    @GetMapping("/name/{categoryName}")
    public ResponseEntity<Category> findByName(@PathVariable String categoryName) {
        Category category = service.findByName(categoryName);
        return ResponseEntity.ok(category);
    }

    @PostMapping
    public ResponseEntity<Category> create(@RequestBody Category category) {
        service.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId) {
        service.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}