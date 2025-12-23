package com.kawashreh.ecommerce.product_service.application.Controller;


import com.kawashreh.ecommerce.product_service.domain.model.Category;
import com.kawashreh.ecommerce.product_service.domain.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping()
    public List<Category> getAll() {
        return service.getAll();
    }
    @GetMapping("/{categoryId}")
    public Category find(@PathVariable UUID categoryId) {
        return service.find(categoryId);
    }
    @GetMapping("/name/{categoryName}")
    public Category findByName(@PathVariable String categoryName) {
        return service.findByName(categoryName);
    }
    @PostMapping()
    public void create(@RequestBody Category category) {
        service.save(category);
    }

    @DeleteMapping("/{categoryId}")
    public void delete(@PathVariable UUID categoryId) {
        service.delete(categoryId);
    }
}
