# Inventory Restore Ceiling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining half of GH #30 by giving product-service's `restoreStock` a real, persisted ceiling — a restore can never exceed what was actually deducted for the same order item, and duplicate/retried deduct or restore calls are idempotent.

**Architecture:** Add a new `InventoryDeductionEntity` ledger table in product-service, keyed by `orderItemId` (unique), recording `deductedQuantity` and `restoredQuantity`. `deductStock` creates a ledger row (or no-ops if one already exists for that `orderItemId`); `restoreStock` checks the ledger under the same pessimistic lock pattern already used for `Inventory` before allowing a restore, rejecting anything that would push `restoredQuantity` past `deductedQuantity`. This requires adding an `orderItemId` parameter to the `deduct`/`restore` HTTP endpoints and their Feign client counterpart in order-service, and switching `OrderServiceImpl` to source that id from the *persisted* `OrderItemEntity` (not the pre-save domain `OrderItem`, whose id is unset before `GenerationType.UUID` assigns it at persist time).

**Tech Stack:** Spring Boot 3 / Java 21, Spring Data JPA (`ddl-auto: update`, no Flyway/Liquibase — new entities create their own table automatically), JUnit 5 + AssertJ + Testcontainers (product-service integration tests), JUnit 5 + Mockito (order-service unit tests), OpenFeign.

## Global Constraints

- Java 21, Spring Boot 3.x. Constructor injection, no field `@Autowired` (per root `CLAUDE.md`).
- IDs are `UUID` across services.
- Public API paths stay centralized in each module's `constants/ApiPaths.java` — no hardcoded route strings in annotations.
- product-service uses `dataAccess/Dao/` (capital D) as the physical directory but `dataAccess.dao` (lowercase) in the package statement of every file in it — this is a pre-existing, intentional-looking quirk (also called out in root `CLAUDE.md`). New files in that directory must match the existing lowercase package statement, not the directory's capitalization.
- Error handling in product-service's `InventoryController` stays in its existing convention: `ResponseEntity<Boolean>`, HTTP 200 always, `false` on any failure/rejection, no 4xx, no `common.ErrorResponse`. Do not introduce new error-response shapes here.
- No gateway route changes — `order-service` calls `product-service` directly via Feign (Kubernetes DNS / gateway URL per `application.yml`), not through the API gateway.
- Follow the spec exactly: `docs/superpowers/specs/2026-08-03-inventory-restore-ceiling-design.md`.

---

### Task 1: `InventoryDeductionEntity` + `InventoryDeductionRepository` (product-service)

**Files:**
- Create: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/entity/InventoryDeductionEntity.java`
- Create: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/Dao/InventoryDeductionRepository.java`
- Test: `product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryDeductionRepositoryTest.java`

**Interfaces:**
- Produces: `InventoryDeductionEntity` with fields `id (UUID)`, `orderItemId (UUID, unique)`, `productVariationId (UUID)`, `deductedQuantity (int)`, `restoredQuantity (int)`, `createdAt`/`updatedAt (Instant)`, plus a `@Builder`. `InventoryDeductionRepository` with `findByOrderItemId(UUID): Optional<InventoryDeductionEntity>` and `findByOrderItemIdWithLock(UUID): Optional<InventoryDeductionEntity>` (`@Lock(PESSIMISTIC_WRITE)`). Task 2/3 consume both of these by name.

- [ ] **Step 1: Write the failing test**

Create `product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryDeductionRepositoryTest.java`:

```java
package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryDeductionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
class InventoryDeductionRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    private UUID orderItemId;
    private UUID productVariationId;

    @BeforeEach
    void setUp() {
        inventoryDeductionRepository.deleteAll();
        orderItemId = UUID.randomUUID();
        productVariationId = UUID.randomUUID();
    }

    @Test
    void save_thenFindByOrderItemId_returnsTheLedgerRow() {
        var entity = InventoryDeductionEntity.builder()
                .orderItemId(orderItemId)
                .productVariationId(productVariationId)
                .deductedQuantity(5)
                .restoredQuantity(0)
                .build();

        inventoryDeductionRepository.save(entity);

        var found = inventoryDeductionRepository.findByOrderItemId(orderItemId);
        assertThat(found).isPresent();
        assertThat(found.get().getDeductedQuantity()).isEqualTo(5);
        assertThat(found.get().getRestoredQuantity()).isEqualTo(0);
    }

    @Test
    void findByOrderItemIdWithLock_returnsEmpty_whenNoRowExists() {
        var found = inventoryDeductionRepository.findByOrderItemIdWithLock(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    void save_rejectsSecondRowForTheSameOrderItemId() {
        inventoryDeductionRepository.save(InventoryDeductionEntity.builder()
                .orderItemId(orderItemId)
                .productVariationId(productVariationId)
                .deductedQuantity(5)
                .restoredQuantity(0)
                .build());

        assertThatThrownBy(() -> {
            inventoryDeductionRepository.saveAndFlush(InventoryDeductionEntity.builder()
                    .orderItemId(orderItemId)
                    .productVariationId(productVariationId)
                    .deductedQuantity(3)
                    .restoredQuantity(0)
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl product-service test -Dtest=InventoryDeductionRepositoryTest`
Expected: compile failure — `InventoryDeductionEntity` and `InventoryDeductionRepository` do not exist yet.

- [ ] **Step 3: Create the entity**

Create `product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/entity/InventoryDeductionEntity.java`:

```java
package com.kawashreh.ecommerce.product_service.dataAccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * GH #30: ledger of how much stock was deducted (and how much of that has since been
 * restored) per order item, so restoreStock can enforce a real ceiling instead of an
 * unconditional add. One row per orderItemId, created by deductStock, updated by
 * restoreStock - never re-created.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "inventory_deduction")
public class InventoryDeductionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    @Column(name = "product_variation_id", nullable = false)
    private UUID productVariationId;

    @Column(name = "deducted_quantity", nullable = false)
    private int deductedQuantity;

    @Column(name = "restored_quantity", nullable = false)
    private int restoredQuantity;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

- [ ] **Step 4: Create the repository**

Create `product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/Dao/InventoryDeductionRepository.java` (physical directory is capitalized `Dao`, matching the sibling `InventoryRepository.java` already there; package statement stays lowercase `dataAccess.dao` to match that same sibling file):

```java
package com.kawashreh.ecommerce.product_service.dataAccess.dao;

import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryDeductionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryDeductionRepository extends JpaRepository<InventoryDeductionEntity, UUID> {

    Optional<InventoryDeductionEntity> findByOrderItemId(UUID orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM InventoryDeductionEntity d WHERE d.orderItemId = :orderItemId")
    Optional<InventoryDeductionEntity> findByOrderItemIdWithLock(UUID orderItemId);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl product-service test -Dtest=InventoryDeductionRepositoryTest`
Expected: PASS (3 tests). Requires a running Docker daemon (Testcontainers).

- [ ] **Step 6: Commit**

```bash
git add product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/entity/InventoryDeductionEntity.java \
        product-service/src/main/java/com/kawashreh/ecommerce/product_service/dataAccess/Dao/InventoryDeductionRepository.java \
        product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryDeductionRepositoryTest.java
git commit -m "feat(product-service): add inventory deduction ledger entity and repository (GH #30)"
```

---

### Task 2: `deductStock` writes a ledger row and is idempotent per `orderItemId`

**Files:**
- Modify: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/InventoryService.java`
- Modify: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/impl/InventoryServiceImpl.java`
- Test: `product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `InventoryDeductionRepository.findByOrderItemIdWithLock(UUID): Optional<InventoryDeductionEntity>` and `.save(InventoryDeductionEntity)` (Task 1).
- Produces: `InventoryService.deductStock(UUID productVariationId, UUID orderItemId, int quantity): boolean` — the new 3-arg signature every other task (3, 4, 5) calls.

- [ ] **Step 1: Update the `InventoryService` interface**

In `product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/InventoryService.java`, replace:

```java
    boolean deductStock(UUID productVariationId, int quantity);

    boolean restoreStock(UUID productVariationId, int quantity);
```

with:

```java
    boolean deductStock(UUID productVariationId, UUID orderItemId, int quantity);

    boolean restoreStock(UUID productVariationId, UUID orderItemId, int quantity);
```

(Both signatures must change together since `InventoryServiceImpl` has to implement both to compile — Step 4 below implements the new bodies for both `deductStock` and `restoreStock` in the same pass, since they share the same lock-then-ledger-check shape.)

- [ ] **Step 2: Write the failing tests**

In `product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryServiceIntegrationTest.java`, every existing call to `inventoryService.deductStock(productVariationId, N)` and `inventoryService.restoreStock(productVariationId, N)` must gain an `orderItemId` argument to compile against the new interface. Replace the **entire file contents** with:

```java
package com.kawashreh.ecommerce.product_service;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.ProductVariationEntity;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class InventoryServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryDeductionRepository inventoryDeductionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    private UUID productVariationId;

    @BeforeEach
    void setUp() {
        inventoryDeductionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariationRepository.deleteAll();
        productRepository.deleteAll();

        ProductEntity product = ProductEntity.builder()
                .name("Test Product")
                .description("Test Description")
                .ownerId(UUID.randomUUID())
                .build();

        product = productRepository.save(product);

        ProductVariationEntity variation = ProductVariationEntity.builder()
                .sku("TEST-SKU-001")
                .name("Test Variation")
                .price(BigDecimal.valueOf(99.99))
                .stockQuantity(0)
                .isActive(true)
                .product(product)
                .build();

        variation = productVariationRepository.save(variation);
        productVariationId = variation.getId();

        InventoryEntity inventory = InventoryEntity.builder()
                .productVariation(variation)
                .quantity(10)
                .warehouseLocation("WAREHOUSE-A")
                .build();

        inventoryRepository.save(inventory);
    }

    @Test
    void findByProductVariationId_shouldReturnInventory() {
        var inventory = inventoryService.findByProductVariationId(productVariationId);

        assertThat(inventory).isNotNull();
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void checkAvailability_shouldReturnTrueWhenSufficientStock() {
        boolean available = inventoryService.checkAvailability(productVariationId, 5);

        assertThat(available).isTrue();
    }

    @Test
    void checkAvailability_shouldReturnFalseWhenInsufficientStock() {
        boolean available = inventoryService.checkAvailability(productVariationId, 15);

        assertThat(available).isFalse();
    }

    @Test
    void deductStock_shouldSucceedWhenSufficientStock() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 3);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void deductStock_shouldFailWhenInsufficientStock() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 15);

        assertThat(result).isFalse();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void deductStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.deductStock(UUID.randomUUID(), UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void restoreStock_shouldSucceed() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean result = inventoryService.restoreStock(productVariationId, orderItemId, 5);

        assertThat(result).isTrue();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restoreStock_shouldFailWhenInventoryNotFound() {
        boolean result = inventoryService.restoreStock(UUID.randomUUID(), UUID.randomUUID(), 5);

        assertThat(result).isFalse();
    }

    @Test
    void deductStock_concurrentDeduction_shouldHandleRaceCondition() throws InterruptedException {
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).get();
        inventory.setQuantity(5);
        inventoryRepository.save(inventory);

        // Two different order items - this is testing the pessimistic lock's
        // serialization of the WHERE-guarded UPDATE, not per-item idempotency (Task 2's
        // idempotency check only no-ops a *repeated* orderItemId, which this deliberately
        // is not).
        Thread thread1 = new Thread(() -> inventoryService.deductStock(productVariationId, UUID.randomUUID(), 5));
        Thread thread2 = new Thread(() -> inventoryService.deductStock(productVariationId, UUID.randomUUID(), 5));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        var finalInventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(finalInventory.getQuantity()).isEqualTo(0);
    }

    // --- GH #28: ProductVariationEntity.stockQuantity must stay in sync with
    // Inventory.quantity, the only two stock-mutating operations in the module. ---

    @Test
    void deductStock_shouldKeepProductVariationStockQuantityInSync() {
        boolean result = inventoryService.deductStock(productVariationId, UUID.randomUUID(), 3);
        assertThat(result).isTrue();

        var variation = productVariationRepository.findById(productVariationId).orElseThrow();
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).orElseThrow();

        assertThat(variation.getStockQuantity()).isEqualTo(inventory.getQuantity());
        assertThat(variation.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void restoreStock_shouldKeepProductVariationStockQuantityInSync() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean result = inventoryService.restoreStock(productVariationId, orderItemId, 5);
        assertThat(result).isTrue();

        var variation = productVariationRepository.findById(productVariationId).orElseThrow();
        var inventory = inventoryRepository.findByProductVariationId(productVariationId).orElseThrow();

        assertThat(variation.getStockQuantity()).isEqualTo(inventory.getQuantity());
        assertThat(variation.getStockQuantity()).isEqualTo(10);
    }

    // --- GH #30 (lock/guard half, already fixed at 38595d6): restoreStock had no lock
    // and no guard, unlike deductStock. ---

    @Test
    void restoreStock_shouldRejectNonPositiveQuantity() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean zeroResult = inventoryService.restoreStock(productVariationId, orderItemId, 0);
        boolean negativeResult = inventoryService.restoreStock(productVariationId, orderItemId, -5);

        assertThat(zeroResult).isFalse();
        assertThat(negativeResult).isFalse();

        // A negative "restore" must never silently deduct stock.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(5);
    }

    // --- GH #30 (upper-bound half - the fix this plan adds): a ledger keyed by
    // orderItemId now bounds restoreStock and makes both deductStock and restoreStock
    // idempotent per orderItemId. ---

    @Test
    void deductStock_calledTwiceWithSameOrderItemId_isIdempotent() {
        UUID orderItemId = UUID.randomUUID();

        boolean first = inventoryService.deductStock(productVariationId, orderItemId, 3);
        boolean second = inventoryService.deductStock(productVariationId, orderItemId, 3);

        assertThat(first).isTrue();
        assertThat(second).isTrue();

        // Only the first call actually decremented stock - the retry must not double-deduct.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void restore_exceedingDeductedAmount_isRejected() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        // Partial restore within the ceiling succeeds.
        boolean partial = inventoryService.restoreStock(productVariationId, orderItemId, 3);
        assertThat(partial).isTrue();

        // 3 already restored + 3 more requested (6) exceeds the 5 that were deducted.
        boolean exceeding = inventoryService.restoreStock(productVariationId, orderItemId, 3);
        assertThat(exceeding).isFalse();

        // Quantity reflects only the successful partial restore: 10 - 5 + 3 = 8.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(8);
    }

    @Test
    void restore_calledTwiceAfterFullRestore_isIdempotentNoOp() {
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        boolean first = inventoryService.restoreStock(productVariationId, orderItemId, 5);
        boolean second = inventoryService.restoreStock(productVariationId, orderItemId, 5);

        assertThat(first).isTrue();
        assertThat(second).isTrue();

        // The second call must not double-credit stock.
        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restore_withNoMatchingLedgerEntry_isRejected() {
        boolean result = inventoryService.restoreStock(productVariationId, UUID.randomUUID(), 5);

        assertThat(result).isFalse();

        var inventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void restoreStock_concurrentRestoreOfSameOrderItem_ceilingHoldsUnderLock() throws InterruptedException {
        // Deduct once, then race two threads each trying to restore the full deducted
        // amount for the SAME orderItemId. The lock must serialize them so only one
        // actually credits stock; the other hits the idempotent-no-op branch, not a
        // double-credit.
        UUID orderItemId = UUID.randomUUID();
        inventoryService.deductStock(productVariationId, orderItemId, 5);

        Thread thread1 = new Thread(() -> inventoryService.restoreStock(productVariationId, orderItemId, 5));
        Thread thread2 = new Thread(() -> inventoryService.restoreStock(productVariationId, orderItemId, 5));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        var finalInventory = inventoryService.findByProductVariationId(productVariationId);
        assertThat(finalInventory.getQuantity()).isEqualTo(10);

        var finalVariation = productVariationRepository.findById(productVariationId).orElseThrow();
        assertThat(finalVariation.getStockQuantity()).isEqualTo(10);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl product-service test -Dtest=InventoryServiceIntegrationTest`
Expected: compile failure — `InventoryServiceImpl` does not yet implement the 3-arg `deductStock`/`restoreStock` signatures.

- [ ] **Step 4: Update `InventoryServiceImpl`**

Replace the full contents of `product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/impl/InventoryServiceImpl.java` with:

```java
package com.kawashreh.ecommerce.product_service.domain.service.impl;

import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryDeductionRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.InventoryRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.dao.ProductVariationRepository;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryDeductionEntity;
import com.kawashreh.ecommerce.product_service.dataAccess.entity.InventoryEntity;
import com.kawashreh.ecommerce.product_service.domain.model.Inventory;
import com.kawashreh.ecommerce.product_service.domain.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final ProductVariationRepository productVariationRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                 InventoryDeductionRepository inventoryDeductionRepository,
                                 ProductVariationRepository productVariationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryDeductionRepository = inventoryDeductionRepository;
        this.productVariationRepository = productVariationRepository;
    }

    @Override
    public Inventory findByProductVariationId(UUID productVariationId) {
        return inventoryRepository.findByProductVariationId(productVariationId)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public boolean checkAvailability(UUID productVariationId, int quantity) {
        return inventoryRepository.findByProductVariationId(productVariationId)
                .map(inventory -> inventory.getQuantity() >= quantity)
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean deductStock(UUID productVariationId, UUID orderItemId, int quantity) {
        // Acquire pessimistic lock first (SELECT ... FOR UPDATE)
        var inventoryOpt = inventoryRepository.findByProductVariationIdWithLock(productVariationId);

        if (inventoryOpt.isEmpty()) {
            logger.warn("Inventory not found for variation: {}", productVariationId);
            return false;
        }

        // GH #30: a ledger row already existing for this orderItemId means this call is a
        // retry (e.g. a Feign retry) of a deduct that already succeeded. Return success
        // without deducting again - orderItemId is unique, so re-inserting would fail
        // anyway, and re-deducting would double-decrement stock for one logical deduction.
        if (inventoryDeductionRepository.findByOrderItemIdWithLock(orderItemId).isPresent()) {
            logger.info("Deduct for orderItemId {} already recorded - treating as a retry, not deducting again",
                    orderItemId);
            return true;
        }

        // Atomic UPDATE with WHERE condition (protected by lock)
        int updated = inventoryRepository.deductQuantity(productVariationId, quantity);

        if (updated > 0) {
            // GH #28: Inventory.quantity is authoritative for stock; keep the duplicate
            // ProductVariationEntity.stockQuantity mirror in sync so the two can't diverge.
            int newQuantity = inventoryOpt.get().getQuantity() - quantity;
            productVariationRepository.updateStockQuantity(productVariationId, newQuantity);

            inventoryDeductionRepository.save(InventoryDeductionEntity.builder()
                    .orderItemId(orderItemId)
                    .productVariationId(productVariationId)
                    .deductedQuantity(quantity)
                    .restoredQuantity(0)
                    .build());

            logger.info("Deducted {} units from inventory for variation {} (orderItemId {})",
                    quantity, productVariationId, orderItemId);
            return true;
        }

        // If update failed, it's due to insufficient stock
        var inventory = inventoryOpt.get();
        logger.warn("Insufficient stock for variation {}: requested {}, available {}",
                productVariationId, quantity, inventory.getQuantity());
        return false;
    }

    @Override
    @Transactional
    public boolean restoreStock(UUID productVariationId, UUID orderItemId, int quantity) {
        // GH #30: guard against non-positive amounts - unlike deductStock's WHERE-guarded
        // update, restoreQuantity is an unconditional add, so a zero/negative quantity would
        // otherwise silently no-op or deduct stock while still being reported as a "restore".
        if (quantity <= 0) {
            logger.warn("Rejected restore of non-positive quantity {} for variation {}", quantity, productVariationId);
            return false;
        }

        // GH #30: acquire the same pessimistic lock deductStock uses, so a restore can never
        // race a concurrent deduct/restore on the same row.
        var inventoryOpt = inventoryRepository.findByProductVariationIdWithLock(productVariationId);

        if (inventoryOpt.isEmpty()) {
            logger.warn("Failed to restore stock for variation {}: inventory not found", productVariationId);
            return false;
        }

        // GH #30 (upper-bound fix): a restore must correspond to a real, previously
        // recorded deduction for this exact orderItemId, and can never push the total
        // restored past what was actually deducted.
        var ledgerOpt = inventoryDeductionRepository.findByOrderItemIdWithLock(orderItemId);
        if (ledgerOpt.isEmpty()) {
            logger.warn("Rejected restore for orderItemId {}: no matching deduction recorded", orderItemId);
            return false;
        }

        var ledger = ledgerOpt.get();
        if (ledger.getRestoredQuantity() == ledger.getDeductedQuantity()) {
            logger.info("orderItemId {} is already fully restored - idempotent no-op", orderItemId);
            return true;
        }

        if (ledger.getRestoredQuantity() + quantity > ledger.getDeductedQuantity()) {
            logger.warn("Rejected restore for orderItemId {}: {} already restored + {} requested exceeds " +
                            "{} deducted", orderItemId, ledger.getRestoredQuantity(), quantity, ledger.getDeductedQuantity());
            return false;
        }

        int updated = inventoryRepository.restoreQuantity(productVariationId, quantity);
        if (updated > 0) {
            // GH #28: keep ProductVariationEntity.stockQuantity in sync, same as deductStock.
            int newQuantity = inventoryOpt.get().getQuantity() + quantity;
            productVariationRepository.updateStockQuantity(productVariationId, newQuantity);

            ledger.setRestoredQuantity(ledger.getRestoredQuantity() + quantity);
            inventoryDeductionRepository.save(ledger);

            logger.info("Restored {} units to inventory for variation {} (orderItemId {})",
                    quantity, productVariationId, orderItemId);
            return true;
        }

        logger.warn("Failed to restore stock for variation {}: inventory not found", productVariationId);
        return false;
    }

    private Inventory toDomain(InventoryEntity entity) {
        return Inventory.builder()
                .id(entity.getId())
                .productVariationId(entity.getProductVariation().getId())
                .quantity(entity.getQuantity())
                .warehouseLocation(entity.getWarehouseLocation())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl product-service test -Dtest=InventoryServiceIntegrationTest`
Expected: PASS (all tests, including the new idempotency/ceiling tests added in Step 2).

- [ ] **Step 6: Commit**

```bash
git add product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/InventoryService.java \
        product-service/src/main/java/com/kawashreh/ecommerce/product_service/domain/service/impl/InventoryServiceImpl.java \
        product-service/src/test/java/com/kawashreh/ecommerce/product_service/InventoryServiceIntegrationTest.java
git commit -m "feat(product-service): enforce a deduction ledger ceiling on restoreStock (GH #30)"
```

---

### Task 3: Wire `orderItemId` through `InventoryController`

**Files:**
- Modify: `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/controller/InventoryController.java`

Note: `ApiPaths.PRODUCT_VARIATION_DEDUCT`/`PRODUCT_VARIATION_RESTORE` are path templates (`/product-variation/{productVariationId}/deduct`); `orderItemId` is a `@RequestParam`, not part of the path, so no `ApiPaths` change is needed for this task.

**Interfaces:**
- Consumes: `InventoryService.deductStock(UUID, UUID, int)` / `restoreStock(UUID, UUID, int)` (Task 2).
- Produces: `PUT /api/v1/inventory/product-variation/{productVariationId}/deduct?quantity=&orderItemId=` and the equivalent `/restore` route — Task 4 (order-service's Feign client) calls these.

- [ ] **Step 1: Update the controller**

In `product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/controller/InventoryController.java`, replace the `deductStock` and `restoreStock` handler methods:

```java
    @PutMapping(ApiPaths.PRODUCT_VARIATION_DEDUCT)
    public ResponseEntity<Boolean> deductStock(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity) {
        boolean success = inventoryService.deductStock(productVariationId, orderItemId, quantity);
        return ResponseEntity.ok(success);
    }

    // GH #30: restoreStock is now bounded by a deduction ledger keyed by orderItemId
    // (InventoryServiceImpl.restoreStock) - a restore can never exceed what was actually
    // deducted for the same order item, and a repeat call for an already-fully-restored
    // orderItemId is an idempotent no-op rather than a double-credit.
    @PutMapping(ApiPaths.PRODUCT_VARIATION_RESTORE)
    public ResponseEntity<Boolean> restoreStock(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity) {
        boolean success = inventoryService.restoreStock(productVariationId, orderItemId, quantity);
        return ResponseEntity.ok(success);
    }
```

This replaces the old GH #30 code comment (the one describing the still-open trust-boundary gap) since the gap is now closed.

- [ ] **Step 2: Compile-check**

Run: `mvn -pl product-service -am compile`
Expected: BUILD SUCCESS. (No dedicated controller test exists for `InventoryController` today — same as before this change — so correctness here is verified via the full module test suite in Task 7 (final verification), not a new test class.)

- [ ] **Step 3: Commit**

```bash
git add product-service/src/main/java/com/kawashreh/ecommerce/product_service/application/controller/InventoryController.java
git commit -m "feat(product-service): accept orderItemId on inventory deduct/restore endpoints (GH #30)"
```

---

### Task 4: `ProductServiceClient` + `OrderServiceImpl` — thread `orderItemId` through order-service

This task changes the Feign client and its only caller together, so every commit in this task leaves `order-service` compiling.

This is the trickiest part of the whole change: `updateProductInventory` and `restoreDeductedInventory` currently operate on `order.getSelectedItems()` — the **pre-save domain `OrderItem` list**, whose `id` field is `null` for a new order (nothing ever sets it before persistence). `OrderItemEntity.id` is `@GeneratedValue(strategy = GenerationType.UUID)`, which Hibernate assigns at persist time - but that id lands on the **entity** object graph (`saved.getSelectedItems()`), which is a *different* set of objects than `order.getSelectedItems()` (built via `OrderMapper.toEntity(order)`, a one-way domain-to-entity copy with no id backfill). So today, if you tried to call `item.getId()` on `order.getSelectedItems()`, you'd always get `null`.

The fix: switch both methods to iterate `saved.getSelectedItems()` (`List<OrderItemEntity>`) instead of `order.getSelectedItems()` (`List<OrderItem>`). `saved` is already computed via `repository.save(entity)` *before* `updateProductInventory` is called in both `create()` and `createOrderFromCart()`, so no reordering is needed - just swap which list gets iterated. Cascade is `CascadeType.ALL` on `OrderEntity.selectedItems` (`dataAccess/entity/OrderEntity.java:51`), so `saved.getSelectedItems()` items have their generated ids populated immediately after `repository.save(entity)` returns, the same way `saved.getId()` is already relied on immediately afterward (e.g. in `invokePayment(saved.getId(), ...)`).

**Files:**
- Modify: `order-service/src/main/java/com/kawashreh/ecommerce/order_service/infrastructure/http/client/ProductServiceClient.java`
- Modify: `order-service/src/main/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImpl.java`

**Interfaces:**
- Produces: `ProductServiceClient.deductInventory(UUID productVariationId, UUID orderItemId, int quantity): Boolean` / `.restoreInventory(UUID productVariationId, UUID orderItemId, int quantity): Boolean`, and `updateProductInventory(OrderEntity savedEntity, List<OrderItemEntity> deductedItems): void` / `restoreDeductedInventory(List<OrderItemEntity> deductedItems, UUID orderId): void` — Task 5's tests must match the Feign signatures via `OrderRepository`'s mocked `save()` echoing the same `OrderItemEntity` objects back (see Task 5).

- [ ] **Step 1: Update the Feign interface**

In `order-service/src/main/java/com/kawashreh/ecommerce/order_service/infrastructure/http/client/ProductServiceClient.java`, replace the `deductInventory`/`restoreInventory` declarations:

```java
    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_DEDUCT)
    Boolean deductInventory(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity);

    @PutMapping(ApiPaths.INVENTORY_BASE + ApiPaths.INVENTORY_RESTORE)
    Boolean restoreInventory(
            @PathVariable UUID productVariationId,
            @RequestParam UUID orderItemId,
            @RequestParam int quantity);
```

- [ ] **Step 2: Add the import to `OrderServiceImpl`**

In `order-service/src/main/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImpl.java`, add to the imports (alongside the existing `dataAccess.mapper.OrderMapper` import):

```java
import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderEntity;
import com.kawashreh.ecommerce.order_service.dataAccess.entity.OrderItemEntity;
```

- [ ] **Step 3: Change `updateProductInventory`'s signature and body**

Replace:

```java
    private void updateProductInventory(Order order, List<OrderItem> deductedItems) {
        for (OrderItem item : order.getSelectedItems()) {
            try {
                boolean deducted = productServiceClient.deductInventory(item.getProductSku(), item.getQuantity());
                if (!deducted) {
                    logger.error("Failed to deduct inventory for product: {}", item.getProductSku());
                    throw new RuntimeException("Failed to deduct inventory for product: " + item.getProductSku());
                }
                // Track success immediately - this item is now deducted on product-service
                // regardless of what happens below (including the redundant retrieveProduct
                // check further down failing), so it must be compensated on any later failure.
                deductedItems.add(item);
                logger.info("Inventory deducted successfully for product: {} - Quantity: {}",
                        item.getProductSku(), item.getQuantity());

                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product != null) {
                    logger.info("Deducting {} units from product {} (SKU: {})",
                            item.getQuantity(), product.getId(), item.getProductSku());
                    logger.info("Inventory updated successfully for product: {}", product.getId());
                } else {
                    throw new RuntimeException("Product not found during inventory update: " + item.getProductSku());
                }
            } catch (Exception e) {
                logger.error("Failed to update inventory for product: {}. Order transaction will be rolled back.",
                        item.getProductSku(), e);
                throw new RuntimeException("Inventory update failed for product " + item.getProductSku() +
                        " - distributed transaction will be rolled back", e);
            }
        }
    }
```

with:

```java
    // GH #30: iterates saved.getSelectedItems() (persisted OrderItemEntity, generated ids
    // populated by cascade-persist inside repository.save() above) rather than
    // order.getSelectedItems() (pre-save domain OrderItem, id always null at this point) -
    // product-service's deduction ledger needs a real, stable orderItemId to key on.
    private void updateProductInventory(OrderEntity savedEntity, List<OrderItemEntity> deductedItems) {
        for (OrderItemEntity item : savedEntity.getSelectedItems()) {
            try {
                boolean deducted = productServiceClient.deductInventory(item.getProductSku(), item.getId(), item.getQuantity());
                if (!deducted) {
                    logger.error("Failed to deduct inventory for product: {}", item.getProductSku());
                    throw new RuntimeException("Failed to deduct inventory for product: " + item.getProductSku());
                }
                // Track success immediately - this item is now deducted on product-service
                // regardless of what happens below (including the redundant retrieveProduct
                // check further down failing), so it must be compensated on any later failure.
                deductedItems.add(item);
                logger.info("Inventory deducted successfully for product: {} - Quantity: {}",
                        item.getProductSku(), item.getQuantity());

                ProductDto product = productServiceClient.retrieveProduct(item.getProductSku());

                if (product != null) {
                    logger.info("Deducting {} units from product {} (SKU: {})",
                            item.getQuantity(), product.getId(), item.getProductSku());
                    logger.info("Inventory updated successfully for product: {}", product.getId());
                } else {
                    throw new RuntimeException("Product not found during inventory update: " + item.getProductSku());
                }
            } catch (Exception e) {
                logger.error("Failed to update inventory for product: {}. Order transaction will be rolled back.",
                        item.getProductSku(), e);
                throw new RuntimeException("Inventory update failed for product " + item.getProductSku() +
                        " - distributed transaction will be rolled back", e);
            }
        }
    }
```

- [ ] **Step 4: Change `restoreDeductedInventory`'s signature and body**

Replace:

```java
    private void restoreDeductedInventory(List<OrderItem> deductedItems, UUID orderId) {
        for (OrderItem item : deductedItems) {
            try {
                Boolean restored = productServiceClient.restoreInventory(item.getProductSku(), item.getQuantity());
```

with:

```java
    private void restoreDeductedInventory(List<OrderItemEntity> deductedItems, UUID orderId) {
        for (OrderItemEntity item : deductedItems) {
            try {
                Boolean restored = productServiceClient.restoreInventory(item.getProductSku(), item.getId(), item.getQuantity());
```

(The rest of the method body — the `if (Boolean.TRUE.equals(restored))`/`else`/`catch` logging — is unchanged; only the two lines above differ. Its Javadoc above the method is also unchanged and still accurate.)

- [ ] **Step 5: Update the two call sites in `create()`**

In `create(Order order)`, replace:

```java
        List<OrderItem> deductedItems = new ArrayList<>();
```

with:

```java
        List<OrderItemEntity> deductedItems = new ArrayList<>();
```

and replace:

```java
            updateProductInventory(order, deductedItems); // remote calls only, no DB transaction held
```

with:

```java
            updateProductInventory(saved, deductedItems); // remote calls only, no DB transaction held
```

(There are two occurrences of each of these two lines in the file — one in `create()`, one in `createOrderFromCart()`. Both need the same two edits.)

- [ ] **Step 6: Update the two call sites in `createOrderFromCart()`**

Apply the identical two edits from Step 5 inside `createOrderFromCart(UUID cartId, UUID buyer)`.

- [ ] **Step 7: Remove the now-unused `OrderItem` import if applicable**

`OrderItem` may still be used elsewhere in this file (e.g. `validateInventoryAvailability(Order order)` still iterates `order.getSelectedItems()` as `List<OrderItem>` — that method is unaffected by this task and keeps using the domain type, since it only reads `productSku`/`quantity`, never an id). Do **not** remove the `com.kawashreh.ecommerce.order_service.domain.model.OrderItem` import — it is still required by `validateInventoryAvailability`.

- [ ] **Step 8: Compile-check**

Run: `mvn -pl order-service -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add order-service/src/main/java/com/kawashreh/ecommerce/order_service/infrastructure/http/client/ProductServiceClient.java \
        order-service/src/main/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImpl.java
git commit -m "feat(order-service): thread orderItemId through ProductServiceClient and OrderServiceImpl (GH #30)"
```

---

### Task 5: Update `OrderServiceImplTest` for the new 3-arg Feign signatures

**Files:**
- Modify: `order-service/src/test/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImplTest.java`

**Interfaces:**
- Consumes: `OrderServiceImpl` as changed in Task 4 (no public interface change - `OrderService`/`OrderServiceImpl`'s public methods `create`/`createOrderFromCart` keep the same signatures; only the private helpers changed).

Since `repository.save(any(OrderEntity.class))` is stubbed in this test file to echo back the exact same `OrderEntity` it was given (`stubSaveToReturnSameEntityAndRecordStatuses`, and the two ad-hoc `thenAnswer(invocation -> invocation.getArgument(0))` stubs), and there is no real Hibernate persistence context in these Mockito-only unit tests to auto-generate `OrderItemEntity.id`, the test's own `OrderItem` builders must explicitly assign an id so it survives the domain→entity mapping (`OrderItemMapper.toEntity` already copies `.id(d.getId())`) and comes back out on `saved.getSelectedItems()`.

- [ ] **Step 1: Give every `OrderItem` in the test fixtures an explicit id**

In `sampleOrder(int quantity)`, replace:

```java
                        OrderItem.builder()
                                .productSku(productVariationId)
                                .quantity(quantity)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
```

with:

```java
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(productVariationId)
                                .quantity(quantity)
                                .unitPrice(BigDecimal.valueOf(99.99))
                                .build()
```

In `multiItemOrder(UUID sku1, int qty1, UUID sku2, int qty2)`, replace both items:

```java
                        OrderItem.builder()
                                .productSku(sku1)
                                .quantity(qty1)
                                .unitPrice(BigDecimal.valueOf(9.99))
                                .build(),
                        OrderItem.builder()
                                .productSku(sku2)
                                .quantity(qty2)
                                .unitPrice(BigDecimal.valueOf(19.99))
                                .build()
```

with:

```java
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(sku1)
                                .quantity(qty1)
                                .unitPrice(BigDecimal.valueOf(9.99))
                                .build(),
                        OrderItem.builder()
                                .id(UUID.randomUUID())
                                .productSku(sku2)
                                .quantity(qty2)
                                .unitPrice(BigDecimal.valueOf(19.99))
                                .build()
```

- [ ] **Step 2: Update every `deductInventory`/`restoreInventory` stub and verification to the 3-arg overload**

The exact orderItemId value is generated fresh per test run (`UUID.randomUUID()` inside the builder above) and not otherwise asserted on, so every stub/verify should match it with `any(UUID.class)` rather than a captured value - this keeps the tests focused on what they were already asserting (which SKU, which quantity) without coupling to the id-plumbing this task adds. Apply these replacements (all in the same file):

Line ~195, ~213, ~237, ~257, ~338, ~357 (`when(productServiceClient.deductInventory(any(UUID.class), anyInt()))`):
```java
        when(productServiceClient.deductInventory(any(UUID.class), any(UUID.class), anyInt())).thenReturn(true);
```
(and the one `.thenReturn(false)` at line ~257 keeps `.thenReturn(false)`, same 3-arg matcher change.)

Line ~223 (`verify(productServiceClient, times(1)).restoreInventory(eq(productVariationId), eq(2));`):
```java
        verify(productServiceClient, times(1)).restoreInventory(eq(productVariationId), any(UUID.class), eq(2));
```

Line ~249 (`verify(productServiceClient, never()).restoreInventory(any(UUID.class), anyInt());`):
```java
        verify(productServiceClient, never()).restoreInventory(any(UUID.class), any(UUID.class), anyInt());
```

Lines ~278-279 (`when(productServiceClient.deductInventory(eq(sku1), eq(2))).thenReturn(true); when(productServiceClient.deductInventory(eq(sku2), eq(3))).thenReturn(false);`):
```java
        when(productServiceClient.deductInventory(eq(sku1), any(UUID.class), eq(2))).thenReturn(true);
        when(productServiceClient.deductInventory(eq(sku2), any(UUID.class), eq(3))).thenReturn(false);
```

Lines ~287-288 (`verify(productServiceClient, times(1)).restoreInventory(eq(sku1), eq(2)); verify(productServiceClient, never()).restoreInventory(eq(sku2), anyInt());`):
```java
        verify(productServiceClient, times(1)).restoreInventory(eq(sku1), any(UUID.class), eq(2));
        verify(productServiceClient, never()).restoreInventory(eq(sku2), any(UUID.class), anyInt());
```

Lines ~301-303 (same two `deductInventory` stubs as above, plus `when(productServiceClient.restoreInventory(eq(sku1), eq(2))).thenThrow(...)`):
```java
        when(productServiceClient.deductInventory(eq(sku1), any(UUID.class), eq(2))).thenReturn(true);
        when(productServiceClient.deductInventory(eq(sku2), any(UUID.class), eq(3))).thenReturn(false);
        when(productServiceClient.restoreInventory(eq(sku1), any(UUID.class), eq(2)))
                .thenThrow(new RuntimeException("restore-service-unreachable"));
```

Line ~313 (`verify(productServiceClient, times(1)).restoreInventory(eq(sku1), eq(2));`):
```java
        verify(productServiceClient, times(1)).restoreInventory(eq(sku1), any(UUID.class), eq(2));
```

Line ~328 (`verify(productServiceClient, never()).deductInventory(any(UUID.class), anyInt());`):
```java
        verify(productServiceClient, never()).deductInventory(any(UUID.class), any(UUID.class), anyInt());
```

- [ ] **Step 3: Run the full order-service test suite**

Run: `mvn -pl order-service test`
Expected: PASS (all tests in `OrderServiceImplTest`, plus everything else in the module - this task and Task 4 only touched the two private helpers and this one test file, nothing else in the module references the old 2-arg Feign signatures).

- [ ] **Step 4: Commit**

```bash
git add order-service/src/test/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImplTest.java
git commit -m "test(order-service): update OrderServiceImplTest for 3-arg deduct/restoreInventory (GH #30)"
```

---

### Task 6: Update ai_docs

**Files:**
- Modify: `.claude/ai_docs/product-service.md`
- Modify: `.claude/ai_docs/order-service.md`

- [ ] **Step 1: Update product-service's repository description**

In `.claude/ai_docs/product-service.md`, replace (around line 100-103):

```
  - `InventoryRepository` — `findByProductVariationId`,
    `findByProductVariationIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`), and two `@Modifying`
    JPQL bulk updates: `deductQuantity` (conditional `WHERE ... quantity >= :quantity`,
    returns rows-updated) and `restoreQuantity` (unconditional add, no upper bound).
```

with:

```
  - `InventoryRepository` — `findByProductVariationId`,
    `findByProductVariationIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`), and two `@Modifying`
    JPQL bulk updates: `deductQuantity` (conditional `WHERE ... quantity >= :quantity`,
    returns rows-updated) and `restoreQuantity` (unconditional add - bounded at the service
    layer instead, see `InventoryDeductionRepository` below and Gotcha #5).
  - `InventoryDeductionRepository` (GH #30) — ledger of deductions keyed by
    `orderItemId` (unique). `findByOrderItemId`, `findByOrderItemIdWithLock`
    (`@Lock(PESSIMISTIC_WRITE)`). Backs `InventoryDeductionEntity`
    (`deductedQuantity`/`restoredQuantity` per order item), created by `deductStock` and
    updated by `restoreStock` - this is what makes `restoreStock`'s ceiling real instead of
    a documented gap.
```

- [ ] **Step 2: Update the HTTP API table**

Replace (around line 135-136):

```
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/deduct?quantity=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/restore?quantity=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |
```

with:

```
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/deduct?quantity=&orderItemId=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/restore?quantity=&orderItemId=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |
```

- [ ] **Step 3: Rewrite Gotcha #5**

Replace the entire Gotcha #5 entry (lines 320-336) with:

```
5. **Fixed (GH #30) — inventory `restoreStock` is now locked, guarded, AND ceilinged.**
   `InventoryServiceImpl.deductStock`/`restoreStock` both take an `orderItemId` parameter
   and are backed by `InventoryDeductionEntity`/`InventoryDeductionRepository`, a ledger
   keyed by `orderItemId` (unique) recording `deductedQuantity`/`restoredQuantity`.
   `deductStock` creates a ledger row on success and is now idempotent per `orderItemId`
   (a repeat call - e.g. a Feign retry - returns `true` without deducting again, since a
   ledger row already exists). `restoreStock` looks up the ledger row under the same
   pessimistic lock used for the `Inventory` row (this lock ordering - inventory row, then
   ledger row - is consistent in both methods, avoiding a lock-order-inversion deadlock)
   and: rejects if no ledger row exists for that `orderItemId` (nothing was ever deducted
   to restore against); is an idempotent no-op if `restoredQuantity == deductedQuantity`
   already; rejects if `restoredQuantity + quantity` would exceed `deductedQuantity`
   (**the actual ceiling**); otherwise restores and increments `restoredQuantity`. This
   closes both halves of the issue's title ("no lock or upper bound") - the concurrency
   half was fixed earlier at `38595d6`, the upper-bound half by this ledger. A genuinely
   concurrent *first* deduct for a brand-new `orderItemId` that somehow races itself (two
   callers submitting the exact same new order item id at once) can still hit the
   ledger table's unique constraint and throw `DataIntegrityViolationException` rather
   than being caught and idempotently handled - left as-is since order-service's actual
   caller never does this (each order item is deducted exactly once, sequentially).
```

- [ ] **Step 4: Update order-service's Feign signature references**

In `.claude/ai_docs/order-service.md`, replace (around line 255):

```
1. `productServiceClient.deductInventory(item.getProductSku(), item.getQuantity())` —
```

with:

```
1. `productServiceClient.deductInventory(item.getProductSku(), item.getId(), item.getQuantity())`
   (GH #30 added the `orderItemId` middle argument, sourced from the persisted
   `OrderItemEntity` - see Gotchas for why `saved.getSelectedItems()` is used instead of
   the pre-save domain `Order`'s items) —
```

Replace (around line 273-276):

```
declares `restoreInventory(UUID productVariationId, int quantity)`
(`infrastructure/http/client/ProductServiceClient.java:33-36`, backed by
`ApiPaths.INVENTORY_RESTORE = /api/v1/inventory/product-variation/{id}/restore`), but it
is **never invoked from any code in this module** (confirmed by grep across `src/main`).
```

with:

```
declares `restoreInventory(UUID productVariationId, UUID orderItemId, int quantity)`
(`infrastructure/http/client/ProductServiceClient.java`, backed by
`ApiPaths.INVENTORY_RESTORE = /api/v1/inventory/product-variation/{id}/restore`). It **is**
invoked, by `restoreDeductedInventory` (see `OrderServiceImpl`, issue #7's compensating-
transaction fix) - the rest of this paragraph and the "no restore" narrative around it
predates that fix and is stale; not rewritten here as it's a pre-existing doc-drift issue
tracked separately under GH #52, out of scope for GH #30.
```

Replace (around line 341, the Feign client table row):

```
| `ProductServiceClient` | `product-service` | `/api/v1/product`, `/api/v1/inventory` | `OrderServiceImpl` (`retrieveProduct`, `retrieveInventory`, `deductInventory`) | `checkInventoryAvailability` and `restoreInventory` are declared but **never called** anywhere in `src/main`. |
```

with:

```
| `ProductServiceClient` | `product-service` | `/api/v1/product`, `/api/v1/inventory` | `OrderServiceImpl` (`retrieveProduct`, `retrieveInventory`, `deductInventory`, `restoreInventory`) | `checkInventoryAvailability` is declared but never called anywhere in `src/main`. `restoreInventory` **is** called (see `restoreDeductedInventory`) - this row was previously stale on that point. |
```

- [ ] **Step 5: Commit**

```bash
git add .claude/ai_docs/product-service.md .claude/ai_docs/order-service.md
git commit -m "docs: update product-service and order-service ai_docs for the GH #30 ledger fix"
```

---

### Task 7: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full product-service test suite**

Run: `mvn -pl product-service -am test`
Expected: BUILD SUCCESS, all tests green (requires Docker for Testcontainers).

- [ ] **Step 2: Run the full order-service test suite**

Run: `mvn -pl order-service -am test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 3: Run the whole reactor build**

Run: `mvn clean verify`
Expected: BUILD SUCCESS across every module - confirms no other module (e.g. `frontend-service`, if it has any direct reference to these Feign signatures - it doesn't per the earlier repo-wide grep, but this is the belt-and-suspenders check the root `CLAUDE.md` working agreement calls for after a boundary change) was missed.

- [ ] **Step 4: Grep for any remaining 2-arg call site**

Run: `grep -rn "deductInventory(\|restoreInventory(\|deductStock(\|restoreStock(" --include=*.java product-service order-service | grep -v "/target/"`
Expected: every match shows 3 arguments (or, for the interface/Feign declarations themselves, three parameters). If any 2-arg call site remains, it was missed by Tasks 2-5 and must be fixed before closing out.

- [ ] **Step 5: Update GH issue #30**

This is a manual step, not a git commit: comment on GH #30 with what was built (ledger-backed ceiling, idempotent deduct/restore) and close it, since both halves of the issue's title are now genuinely fixed.
