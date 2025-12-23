package com.kawashreh.ecommerce.product_service.application.Controller;


import com.kawashreh.ecommerce.product_service.domain.model.Category;
import com.kawashreh.ecommerce.product_service.domain.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping("/")
    public List<Category> getAll() {
        return service.getAll();
    }
    @GetMapping("/{}")
    public List<Category> find(@PathVariable) {
        return service.getAll();
    }

    @PostMapping("/")
    public void create(@RequestBody Category category) {
        service.save(category);
    }
}
