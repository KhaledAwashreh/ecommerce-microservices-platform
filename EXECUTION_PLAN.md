# Execution Plan: Implementing HTTP Mapper Pattern in Product Service

## Overview
This document outlines the plan to implement the HTTP Mapper pattern from UserService into the Product Service, following the same architectural approach for clean separation between application layer (DTOs) and domain layer (Models).

---

## Pattern Analysis (from UserService)

### Current Implementation Structure
The UserService uses a clean **DTO-to-Domain mapping pattern**:

1. **UserHttpMapper** (`mapper/UserHttpMapper.java`)
   - Static utility mapper class
   - Two-way conversion methods:
     - `toDto(User)`: Domain Model → DTO (for HTTP responses)
     - `toDomain(UserDto)`: DTO → Domain Model (for HTTP requests)
   - Null-safe with null checks
   - Uses builder pattern for object construction
   - Decorated with `@Data` and `@Builder` annotations

2. **UserDto** (`dto/UserDto.java`)
   - Data Transfer Object for HTTP layer
   - Contains all user fields with appropriate types
   - Uses Lombok annotations (`@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`)
   - Fields: `id`, `name`, `username`, `email`, `birthdate`, `phone`, `gender`, `createdAt`, `updatedAt`

3. **UserController** (`controller/UserController.java`)
   - Uses mapper in all endpoints:
     - **GET all**: `service.getAll().stream().map(UserHttpMapper::toDto).collect(toList())`
     - **GET by ID**: `UserHttpMapper.toDto(user)`
     - **POST**: `UserHttpMapper.toDomain(userDto)` → service → `UserHttpMapper.toDto(saved)`
   - Returns `ResponseEntity<UserDto>` instead of domain objects
   - Separates HTTP concerns from business logic

---

## Product Service Current State

### Entities to Handle
1. **Product** - Main product entity
2. **Category** - Product categories
3. **ProductReview** - Product reviews
4. **ProductVariation** - Product variations
5. **Attribute** - Product attributes (if used)

### Current Issues (Pattern Violations)
1. **ProductController** returns `Product` (domain model) directly in HTTP responses
2. **CategoryController** returns `Category` (domain model) directly in HTTP responses
3. **ProductReviewController** uses `ProductReviewDto` but returns `ProductReview` (domain model) in responses
4. No HTTP mappers exist for conversion between domain and DTO layers
5. DTO is incomplete/inconsistent (only has basic ProductReviewDto with limited fields)

---

## Implementation Plan

### Phase 1: Create DTOs for all Entities
Create comprehensive DTOs that mirror domain models with all necessary fields:

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/dto/ProductDto.java`**
- Map all fields from Product domain model
- Include: `id`, `ownerId`, `name`, `description`, `categories`, `createdAt`, `updatedAt`, `thumbnailUrl`
- Add nested `CategoryDto` for embedded categories
- Use Lombok annotations consistent with UserDto

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/dto/CategoryDto.java`**
- Map all fields from Category domain model
- Include: `id`, `name`, `description`
- Use Lombok annotations consistent with UserDto

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/dto/ProductReviewDto.java`** (Update)
- Expand existing DTO with all fields from ProductReview domain model
- Include: `id`, `userId`, `productId`, `review`, `stars`, `createdAt`, `updatedAt` (if applicable)
- Change structure to include `productId` instead of nested `Product`
- Use consistent Lombok annotations

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/dto/ProductVariationDto.java`** (Optional)
- If ProductVariation endpoints exist, create corresponding DTO

### Phase 2: Create HTTP Mappers
Create mapper classes following UserHttpMapper pattern:

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/mapper/ProductHttpMapper.java`**
- Static utility class (private constructor to prevent instantiation)
- Implement bidirectional mapping:
  - `toDto(Product)`: Product → ProductDto
  - `toDomain(ProductDto)`: ProductDto → Product
- Handle nested CategoryDto conversion
- Include null checks for safety
- Use builder pattern for construction

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/mapper/CategoryHttpMapper.java`**
- Static utility class
- Implement bidirectional mapping:
  - `toDto(Category)`: Category → CategoryDto
  - `toDomain(CategoryDto)`: CategoryDto → Category
- Include null checks
- Use builder pattern

**File: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/mapper/ProductReviewHttpMapper.java`**
- Static utility class
- Implement bidirectional mapping:
  - `toDto(ProductReview)`: ProductReview → ProductReviewDto
  - `toDomain(ProductReviewDto)`: ProductReviewDto → ProductReview
- Note: Handle `productId` extraction from nested Product object in domain
- Include null checks
- Use builder pattern

### Phase 3: Update Controllers
Refactor controllers to use mappers consistently:

**File: `ProductController.java`** (Update)
- Change return types from `Product` to `ProductDto`
- Apply mapper in all endpoints:
  - GET all: `.map(ProductHttpMapper::toDto)`
  - GET by ID: `ProductHttpMapper.toDto(product)`
  - POST: `ProductHttpMapper.toDomain(dto)` → service → `ProductHttpMapper.toDto(result)`
  - DELETE: no change needed
- Return `ResponseEntity<ProductDto>` instead of `ResponseEntity<Product>`

**File: `CategoryController.java`** (Update)
- Change return types from `Category` to `CategoryDto`
- Apply mapper in all endpoints:
  - GET all: `.map(CategoryHttpMapper::toDto)`
  - GET by ID: `CategoryHttpMapper.toDto(category)`
  - POST: `CategoryHttpMapper.toDomain(dto)` → service → `CategoryHttpMapper.toDto(result)`
  - DELETE: no change needed
- Return `ResponseEntity<CategoryDto>` instead of `ResponseEntity<Category>`

**File: `ProductReviewController.java`** (Update)
- Change return types from `ProductReview` to `ProductReviewDto`
- Apply mapper in all endpoints:
  - GET all: `.map(ProductReviewHttpMapper::toDto)`
  - GET by ID: `ProductReviewHttpMapper.toDto(review)`
  - POST: Already using DTO but update to use mapper for consistency
  - DELETE: no change needed
- Return `ResponseEntity<ProductReviewDto>` instead of `ResponseEntity<ProductReview>`

---

## Expected Benefits

1. **Clean Architecture**: Clear separation between HTTP/Application layer and Domain layer
2. **Consistency**: All services follow the same architectural pattern
3. **Type Safety**: Controllers return DTOs, preventing accidental exposure of internal domain logic
4. **Maintainability**: Centralized mapping logic makes changes easier
5. **Flexibility**: DTOs can evolve independently from domain models
6. **Reusability**: Mappers can be used across multiple endpoints and services

---

## File Summary Table

| File Type | File Name | Action | Path |
|-----------|-----------|--------|------|
| DTO | ProductDto.java | **CREATE** | `application/dto/` |
| DTO | CategoryDto.java | **CREATE** | `application/dto/` |
| DTO | ProductReviewDto.java | **UPDATE** | `application/dto/` |
| Mapper | ProductHttpMapper.java | **CREATE** | `application/mapper/` |
| Mapper | CategoryHttpMapper.java | **CREATE** | `application/mapper/` |
| Mapper | ProductReviewHttpMapper.java | **CREATE** | `application/mapper/` |
| Controller | ProductController.java | **UPDATE** | `application/controller/` |
| Controller | CategoryController.java | **UPDATE** | `application/controller/` |
| Controller | ProductReviewController.java | **UPDATE** | `application/controller/` |

---

## Implementation Order

1. ✅ Create `ProductDto.java`
2. ✅ Create `CategoryDto.java`
3. ✅ Update `ProductReviewDto.java`
4. ✅ Create `ProductHttpMapper.java`
5. ✅ Create `CategoryHttpMapper.java`
6. ✅ Create `ProductReviewHttpMapper.java`
7. ✅ Update `ProductController.java`
8. ✅ Update `CategoryController.java`
9. ✅ Update `ProductReviewController.java`

---

## Testing Considerations

After implementation, verify:
- [ ] DTOs properly serialize/deserialize to/from JSON
- [ ] Mappers handle null objects safely
- [ ] Controllers return DTOs in HTTP responses
- [ ] Nested objects (e.g., categories in products) map correctly
- [ ] All CRUD operations work correctly with new DTOs
- [ ] No compilation errors or warnings

---

## Rollback Plan

If issues arise, revert changes using git:
```bash
git checkout -- product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/
```

This will restore all files to their previous state before implementation.
