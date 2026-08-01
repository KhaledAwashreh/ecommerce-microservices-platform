# product-service

> **Amendment (GH #17 fix):** this doc predates the fix and the "No
> authentication/authorization code exists in this module" statement in the Security
> section is now **stale**. Current state: `infastructure/security/JwtAuthFilter` +
> `JwtService` validate every request's bearer token locally (except `/actuator/**`)
> before it reaches a controller, using the same shared HMAC secret as user-service/
> api-gateway (`constants/JwtConstants`). A new `IncomingAuthHeaderFeignInterceptor`
> forwards the caller's Authorization header onto this service's own outbound Feign
> calls (e.g. to user-service), since those now require a token too. The rest of this
> document is otherwise still accurate as of the fix; it has not been fully
> regenerated.

## Purpose

Owns products, product variations, categories, inventory, and product reviews for the
e-commerce platform. Exposes REST endpoints behind the API gateway, persists to its own
Postgres schema (`productdb`) via Spring Data JPA, caches read paths in Redis, and calls
out to `user-service` (through the gateway) via OpenFeign to validate that a user exists
before creating a product or a review. Built on Spring Boot 3.4.12 / Java 21.

## Package layout

```
com.kawashreh.ecommerce.product_service
├── ProductServiceApplication.java        # @SpringBootApplication, @EnableFeignClients, @EnableTransactionManagement
├── application/
│   ├── controller/    ProductController, CategoryController, InventoryController, ProductReviewController, ProductVariationController
│   ├── dto/            ProductDto, CategoryDto, InventoryDto, ProductReviewDto, ProductVariationDto
│   ├── mapper/          ProductHttpMapper, CategoryHttpMapper, InventoryHttpMapper, ProductReviewHttpMapper, ProductVariationHttpMapper (domain <-> DTO)
│   └── service/          ProductApplicationService, ReviewApplicationService, ProductVariationApplicationService (orchestrate domain services + UserServiceClient)
├── constants/
│   ├── ApiPaths.java     PRODUCT_BASE, INVENTORY_BASE + variation sub-paths. No CATEGORY/REVIEW paths (see Gotchas).
│   └── CacheConstants.java  cache names (inconsistent casing — see Gotchas)
├── dataAccess/                                             # NOTE: capital-D "Dao" is a repo-wide known deviation
│   ├── Dao/            CategoryRepository, InventoryRepository, ProductRepository, ProductReviewRepository, ProductVariationRepository
│   ├── entity/           AttributeEntity, CategoryEntity, InventoryEntity, ProductEntity, ProductReviewEntity, ProductVariationEntity
│   └── mapper/            AttributeMapper, CategoryMapper, InventoryMapper, ProductMapper, ProductReviewMapper, ProductVariationMapper (entity <-> domain)
├── domain/
│   ├── model/            Attribute, Category, Inventory, Product, ProductReview, ProductVariation (plain POJOs)
│   └── service/ (+ impl/)  ProductService, CategoryService, ProductVariationService, ProductReviewService, InventoryService
├── infastructure/                                          # NOTE: misspelled package name, repo-wide known deviation
│   ├── cache/            CacheConfig (Redis cache manager + RedisTemplate beans)
│   └── http/
│       ├── client/         UserServiceClient (Feign, calls user-service through the gateway)
│       └── dto/             UserDto
└── infra/models/Attachment.java   # SEPARATE, unrelated package from "infastructure" — dead @Entity, see Gotchas
```

## Domain model

| Domain POJO | Entity | Table | Notes |
|---|---|---|---|
| `Product` | `ProductEntity` | `Product` (mixed-case table name) | `ownerId` (UUID), `categories` (List\<Category>), timestamps, `thumbnailUrl`. |
| `Category` | `CategoryEntity` | `categry` (misspelled) | id/name/description only. |
| `ProductVariation` | `ProductVariationEntity` | `product_variation` | sku (unique), `stockQuantity` (duplicate of `Inventory.quantity`, see Gotchas), price, isActive, `attachments` (`List<UUID>` `@ElementCollection`), `attributes` (`List<Attribute>`), owning `Product`. |
| `Attribute` | `AttributeEntity` | `attribute` | Generic name/value pair, `@OneToMany` from `ProductVariationEntity` via a miswired join column (see Gotchas). |
| `Inventory` | `InventoryEntity` | `inventory` | `quantity`, `reservedQuantity` (defined, never written — see Gotchas), `warehouseLocation`, `getAvailableQuantity() = quantity - reservedQuantity`. One inventory row per `ProductVariation` (`@ManyToOne` FK `product_variation_id`, not enforced unique at JPA level). |
| `ProductReview` | `ProductReviewEntity` | `productReview` | `userId`, owning `Product` (`@ManyToOne`, required), `review`, `stars` (`int`, no range validation), timestamps (never auto-populated — see Gotchas). |
| n/a | `Attachment` (`infra/models`) | `attachment` (implicit) | Isolated, unused `@Entity` — dead code, see Gotchas. |

Domain models are plain Lombok `@Data @Builder` classes, separate from JPA entities, mapped
by hand-written static mapper classes in `dataAccess/mapper` (entity↔domain) and
`application/mapper` (domain↔HTTP DTO), matching the repo's layered convention.

`Product.categories` maps through a `@ManyToMany` on `ProductEntity` using a join table
literally named `category` (`@JoinTable(name = "category", ...)`) — see Gotchas for why this
collides in meaning (not in name) with the misspelled `categry` table.

## Persistence

- Schema source: `spring.jpa.hibernate.ddl-auto: update` in `application.yml` (Hibernate
  auto-DDL, no Flyway/Liquibase migrations in this module). Integration tests use
  `create-drop` against a Testcontainers Postgres (`BaseIntegrationTest.java`).
- Dialect: `org.hibernate.dialect.PostgreSQLDialect`. `show-sql`/`format_sql` enabled.
- Repositories (all Spring Data JPA, package `dataAccess/Dao`, package-private `dao` in
  code despite the directory being capitalized `Dao`):
  - `ProductRepository` — plain `JpaRepository`, no custom queries.
  - `CategoryRepository` — adds `findByName(String)`.
  - `ProductVariationRepository` — `findByProductId` (List), `deleteByProductId`,
    `countByProductId`. (A never-called `Page` overload was removed — issue #51, see
    Gotchas.)
  - `ProductReviewRepository` — `findByProductId` (List), `findByUserId`,
    `countByProductId`. (Same dead `Page` overload removed here too.)
  - `InventoryRepository` — `findByProductVariationId`,
    `findByProductVariationIdWithLock` (`@Lock(PESSIMISTIC_WRITE)`), and two `@Modifying`
    JPQL bulk updates: `deductQuantity` (conditional `WHERE ... quantity >= :quantity`,
    returns rows-updated) and `restoreQuantity` (unconditional add, no upper bound).

## HTTP API

### ProductController — `/api/v1/product` (`ApiPaths.PRODUCT_BASE`)

| Method | Path | Request | Response | Status | Auth |
|---|---|---|---|---|---|
| GET | `/api/v1/product` | — | `List<ProductDto>` | 200 | none enforced in this module |
| GET | `/api/v1/product/{productId}` | — | `ProductDto` | 200 / 404 if not found | none |
| POST | `/api/v1/product` | `ProductDto` | `ProductDto` | 201 (even on failure — see Gotchas) | none |
| DELETE | `/api/v1/product/{productId}` | — | — | 204 (always, even if id doesn't exist) | none |

No PUT/update endpoint exists even though `ProductService.update()` is implemented
(dead code — see Gotchas).

### CategoryController — `/api/v1/categories` (hardcoded, not in `ApiPaths` — see Gotchas)

| Method | Path | Request | Response | Status | Auth |
|---|---|---|---|---|---|
| GET | `/api/v1/categories` | — | `List<CategoryDto>` | 200 | none |
| GET | `/api/v1/categories/{categoryId}` | — | `CategoryDto` | 200 / 404 | none |
| GET | `/api/v1/categories/name/{categoryName}` | — | `CategoryDto` | 200 / 404 | none |
| POST | `/api/v1/categories` | `CategoryDto` | `CategoryDto` | 201 | none |
| DELETE | `/api/v1/categories/{categoryId}` | — | — | 204 (always) | none |

### InventoryController — `/api/v1/inventory` (`ApiPaths.INVENTORY_BASE`)

| Method | Path | Request | Response | Status | Auth |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/product-variation/{productVariationId}` | — | `InventoryDto` | 200 / 404 | none |
| GET | `/api/v1/inventory/product-variation/{productVariationId}/availability?quantity=` | — | `Boolean` | 200 (`false` if not found, indistinguishable from "not enough stock") | none |
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/deduct?quantity=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |
| PUT | `/api/v1/inventory/product-variation/{productVariationId}/restore?quantity=` | — | `Boolean` | 200 (`false` on failure, no 4xx) | none |

### ProductReviewController — `/api/v1/productReview` (hardcoded, not in `ApiPaths`)

| Method | Path | Request | Response | Status | Auth |
|---|---|---|---|---|---|
| GET | `/api/v1/productReview` | — | `List<ProductReviewDto>` | 200 | none |
| GET | `/api/v1/productReview/{productId}` | — | `List<ProductReviewDto>` (reviews for a product) | 200 | none |
| GET | `/api/v1/productReview/{reviewId}` | — | `ProductReviewDto` (single review) | 200 / 404 | none |
| POST | `/api/v1/productReview` | `ProductReviewDto` | `ProductReviewDto` | 201 (even on failure — see Gotchas) | none |
| DELETE | `/api/v1/productReview/{reviewId}` | — | — | 204 (always) | none |

**Critical:** `getReviewsForProduct` (`GET /{productId}`) and `findById` (`GET
/{reviewId}`) are both mapped to the same URL template shape (`/{anyVariableName}`) on the
same controller with the same HTTP verb. Spring registers both at startup without error
(the raw pattern strings differ), but any actual `GET
/api/v1/productReview/<uuid>` request matches both mappings equally, and
`RequestMappingInfoHandlerMapping` throws `IllegalStateException: Ambiguous handler
methods mapped` at request time — see Gotchas #1.

No request body validation (`@Valid`/Bean Validation annotations) is used anywhere despite
`spring-boot-starter-validation` being a declared Maven dependency.

### ProductVariationController — `/api/v1/product-variation` (`ApiPaths.PRODUCT_VARIATION_BASE`)

| Method | Path | Request | Response | Status | Auth |
|---|---|---|---|---|---|
| GET | `/api/v1/product-variation` | — | `List<ProductVariationDto>` | 200 | none |
| GET | `/api/v1/product-variation/{productVariationId}` | — | `ProductVariationDto` | 200 / 404 | none |
| GET | `/api/v1/product-variation/product/{productId}` | — | `List<ProductVariationDto>` | 200 | none |
| POST | `/api/v1/product-variation` | `ProductVariationDto` (requires `productId`) | `ProductVariationDto` | 201 (body null if `productId` doesn't resolve to a product — same "success-status on failure" pattern as `ProductController`/`ProductReviewController`, see Gotchas) | none |
| PUT | `/api/v1/product-variation/{productVariationId}` | `ProductVariationDto` | `ProductVariationDto` | 200 / 404 if the variation doesn't exist | none |
| DELETE | `/api/v1/product-variation/{productVariationId}` | — | — | 204 (always) | none |

Closes the previously dead `ProductVariationService`/`ProductVariationServiceImpl` CRUD (create,
update, delete, find, findByProductId) to HTTP. `ProductVariationDto` exposes only the
variation's own scalar/FK fields (`id`, `productId`, `sku`, `name`, `price`, `stockQuantity`,
`isActive`, `thumbnailUrl`, `createdAt`, `updatedAt`); it deliberately does not expose
`attachments` or `attributes` — see Gotchas.

`ProductVariationApplicationService.createVariation` looks up the parent `Product` via
`ProductService.find(productId)` before attaching it and saving (mirrors
`ReviewApplicationService.createReview`'s pattern of resolving a required parent before
persisting). `update` does not use the application service: the controller re-fetches the
existing variation by id first, copies the incoming DTO's scalar fields onto a fresh domain
object, and reattaches the *existing* `product` association (a PUT here cannot move a
variation to a different product — that would need a separate, explicit operation).

To avoid the same "two single-path-variable GET mappings" ambiguity documented above for
`ProductReviewController`, `findById` uses a bare `/{productVariationId}` template while
`findByProductId` uses a two-segment `/product/{productId}` template — the literal `product`
segment keeps Spring's route matching unambiguous.

## Outbound dependencies

- **`UserServiceClient`** (`infastructure/http/client/UserServiceClient.java`) — OpenFeign
  client, `@FeignClient(name = "user-service")`, `GET /api/v1/user/{userId}`. Routed through
  the API gateway via `spring.cloud.openfeign.client.config.user-service.url`
  (`application.yml`: `${GATEWAY_URL:http://api-gateway:8765}`; overridden per-profile in
  `application-ide.yml`). Used by `ProductApplicationService.createProduct` and
  `ReviewApplicationService.createReview` to confirm a user exists (and, for reviews, to
  compare `user.getId()` against `product.getOwnerId()`) before persisting.
- **Failure handling:** no explicit fallback, circuit breaker, or try/catch around the Feign
  call in either application service — a Feign exception (e.g. user-service down, 404, 5xx)
  propagates uncaught out of the controller. `resilience4j-retry` and `feign-micrometer`
  are on the classpath but no `@Retry`/`@CircuitBreaker` annotation or resilience4j
  properties exist anywhere in this module's source — the dependencies are unused.

## Configuration

| Property | Source | Default | Notes |
|---|---|---|---|
| `spring.datasource.url` | `application.yml` | `jdbc:postgresql://localhost:5432/productdb` | Overridden by env var `SPRING_DATASOURCE_URL`; further overridden per-profile (`application-local.yml`: port 5433; `application-ide.yml`: port 5434). |
| `spring.datasource.username/password` | `application.yml` | `postgres`/*(none — required)* | `password: ${SPRING_DATASOURCE_PASSWORD}` has no default (previously `:postgres`) — a missing env var fails startup. `application-local.yml` still hardcodes password `test1234` (dev-only, per repo convention). |
| `spring.jpa.hibernate.ddl-auto` | `application.yml` | `update` | Test profile uses `create-drop`. |
| `spring.data.redis.host/port` | `application.yml` | `localhost`/`6379` | Env vars `SPRING_DATA_REDIS_HOST`/`SPRING_DATA_REDIS_PORT`. `application-local.yml` hardcodes host `redis` (Docker Compose service name) even though it's meant for "running from IDE without Docker" per its own header comment — inconsistent with its stated purpose. |
| `server.port` | `application.yml` | `8080` | `application-ide.yml` sets `8082`. Test profile (`application-test.yml`) uses `0` (random port). Dockerfile comment claims "Port is dynamically assigned (server.port=0 in application.properties)" — false for the shipped `application.yml`/Docker profile, only true under the `test` profile; see Gotchas. |
| `spring.cloud.openfeign.client.config.user-service.url` | `application.yml` | `${GATEWAY_URL:http://api-gateway:8765}` | Points Feign at the gateway, not directly at user-service, matching repo convention. |
| `spring.cloud.config.enabled` / `discovery.enabled` | `application.yml` | `false` | Config server / discovery client disabled; this project uses Kubernetes DNS, not Eureka/Config Server (the module's `bootstrap.properties` configured both and was removed as dead config — issue #50). |
| `management.zipkin.tracing.endpoint` | `application.yml` | `${ZIPKIN_BASE_URL:http://zipkin:9411}/api/v2/spans` | `management.tracing.sampling.probability` = `1.0` (trace everything). |
| `management.endpoints.web.exposure.include` | `application.yml` | `health,info,metrics,prometheus` | No auth restricting actuator endpoints within this module. |

Profiles present: default (`application.yml`), `local` (`application-local.yml`, IDE-without-Docker),
`ide` (`application-ide.yml`, product-service in IDE / rest in Docker), `test`
(`src/test/resources/application-test.yml`, used by `@ActiveProfiles("test")` but largely
superseded by Testcontainers dynamic properties in `BaseIntegrationTest`). The module
previously had a `bootstrap.properties` configuring Spring Cloud Config + Eureka; it was
removed (issue #50) since `spring.cloud.config.enabled=false` in `application.yml` made it
inert.

## Caching

Redis-backed via `infastructure/cache/CacheConfig` (`RedisCacheManager` bean): default TTL
10 minutes, null values disabled, keys as plain strings, values as JSON
(`GenericJackson2JsonRedisSerializer` with default typing enabled — see Gotchas for the
security implication).

| Cache name (`CacheConstants`) | Populated by | Evicted by |
|---|---|---|
| `product_by_id` (lower_snake, inconsistent with the others) | `ProductServiceImpl.find` (`@Cacheable`, key `#id`) | `ProductServiceImpl.update`/`delete` (`@CacheEvict(allEntries = true)`). **Not evicted by `save` (create)** — see Gotchas. |
| `PRODUCT_REVIEW_BY_USER_ID` | Declared but never used on any method — dead constant. | n/a |
| `PRODUCT_REVIEW_BY_PRODUCT_ID` | `ProductReviewServiceImpl.findByProductId` (`@Cacheable`, key `#productId`) | **Never evicted anywhere** — stale after any create/update/delete of a review for that product — see Gotchas. |
| `PRODUCT_VARIATION_BY_PRODUCT_ID` | `ProductVariationServiceImpl.findByProductId` (`@Cacheable`, key `#productId`) | `ProductVariationServiceImpl.update`/`delete` via `@CacheEvict(key = "#result.productId")` — **broken, see Gotchas #2 (critical)**. |

`CategoryServiceImpl` has no caching at all despite `CategoryController` being a plain
read-heavy CRUD controller.

## Security

No authentication/authorization code exists in this module (no filters, no Spring
Security dependency, no JWT handling). Per the root `CLAUDE.md`, auth is expected to be
enforced upstream by `api-gateway`'s `JwtAuthFilter`; product-service trusts the gateway
and performs no owner/identity checks except the manual `ownerId`/`userId` comparisons in
`ReviewApplicationService.createReview` and `ProductReviewServiceImpl.save` (self-review
prevention, not access control). All endpoints are open to any caller that can reach the
service directly (e.g. inside the cluster network) since there is no per-request identity
check in product-service itself.

`CacheConfig`'s `ObjectMapper.activateDefaultTyping(..., ObjectMapper.DefaultTyping.NON_FINAL, ...)`
embeds Java class names in cached JSON (Redis) — a polymorphic deserialization gadget-chain
risk if the Redis instance is ever writable by an untrusted party, since Jackson will
instantiate whatever class name it finds in the `@class`/type property when reading cache
entries back. Mitigated somewhat by `BasicPolymorphicTypeValidator.allowIfBaseType(Object.class)`,
which is very permissive (allows any base type).

## Tests

- `src/test/java/.../BaseIntegrationTest.java` — Testcontainers Postgres 16-alpine base
  class (`@SpringBootTest`, `@Testcontainers`, `@ActiveProfiles("test")`), dynamically
  overrides datasource properties and forces `ddl-auto=create-drop`.
- `src/test/java/.../InventoryServiceIntegrationTest.java` — the only test class in the
  module. Covers `InventoryService`: find, `checkAvailability` (sufficient/insufficient),
  `deductStock` (success, insufficient stock, inventory-not-found), `restoreStock` (success,
  inventory-not-found), and one concurrency test
  (`deductStock_concurrentDeduction_shouldHandleRaceCondition`) that fires two threads each
  deducting 5 units from a starting quantity of 5 and asserts the final quantity is exactly
  `0` (i.e. only one deduction should succeed) — exercising the pessimistic-lock +
  conditional-`UPDATE` combination in `InventoryServiceImpl.deductStock`.
- `src/test/java/.../application/controller/ProductReviewControllerTest.java` — `@WebMvcTest`
  slice test (no Docker needed), regression coverage for the ambiguous-route fix (GH #1).
- `src/test/java/.../application/controller/ProductVariationControllerTest.java` — `@WebMvcTest`
  slice test (no Docker needed) covering the new `ProductVariationController` (GH #14): list,
  find by id (found/404), find by product, create (201), update (200/404), delete (204).
- No tests exist for `ProductService`, `CategoryService`, `ProductReviewService`,
  `ProductVariationService`, any mapper, `ProductApplicationService`,
  `ReviewApplicationService`, or `ProductVariationApplicationService`.
- Run: `mvn -pl product-service test` (requires a running Docker daemon for
  Testcontainers). `mvn -pl product-service -am test` to build dependent modules first.

## Gotchas

1. **Critical — ambiguous route, likely runtime 500s.** `ProductReviewController.java:39`
   (`GET /{productId}`) and `ProductReviewController.java:49` (`GET /{reviewId}`) both
   register a single-path-variable GET mapping directly under
   `/api/v1/productReview`. Spring allows both to register at startup (pattern strings
   differ), but any concrete request to `GET /api/v1/productReview/<value>` matches both
   equally and Spring throws `IllegalStateException: Ambiguous handler methods mapped`.
   Neither "get reviews for a product" nor "get review by id" is reliably callable.
2. **Critical — broken cache eviction, likely runtime SpEL error.**
   `ProductVariationServiceImpl.java:43-49` and `:52-61`: `@CacheEvict(value =
   CacheConstants.PRODUCT_VARIATION_BY_PRODUCT_ID, key = "#result.productId")` is applied
   to `update(ProductVariation)` and `delete(UUID)`, both `void`-returning methods. `#result`
   is `null` for a void method, so `#result.productId` fails SpEL evaluation
   (`SpelEvaluationException`/NPE) on every call — and even if `#result` were populated,
   `ProductVariation` has no `productId` property (it has `product`, a `Product` object).
   This would break every update/delete of a product variation at runtime (untested — no
   test covers `ProductVariationServiceImpl`).
3. **High — wrong field mapped.** `ProductReviewHttpMapper.java:20`: `toDto` sets
   `.createdAt(review.getProduct().getCreatedAt())` — the *product's* creation timestamp,
   not the review's. `review.getUpdatedAt()` is also never mapped into the DTO at all. Also
   NPE risk: `review.getProduct()` is dereferenced unguarded on this line even though line
   17 null-checks it for `productId`.
4. **High — POST endpoints return 201 with a null/empty body on business-rule failure.**
   `ProductController.java:47-52`: if `ProductApplicationService.createProduct` returns
   `null` (user-service lookup failed), the controller still responds `201 Created` with an
   empty body instead of a 4xx. Same pattern in `ProductReviewController.java:58-64` when
   `ReviewApplicationService.createReview` returns `null` (self-review attempt, unknown
   user, or unknown product).
5. **High — inventory `restoreStock` unbounded and unlocked.**
   `InventoryRepository.java:27-29` (`restoreQuantity`) and
   `InventoryServiceImpl.java:66-75`: unlike `deductStock`, `restoreStock` takes no
   pessimistic lock and has no upper-bound guard (no check against original/reserved
   quantity), so a caller can restore more stock than was ever deducted, and there is no
   protection against double-restore races (two concurrent restores of the same quantity
   both succeed additively, which may or may not be desired but is inconsistent with the
   locked, guarded `deductStock` path).
6. **Fixed (GH #14) — `ProductVariationController` now exists.** `ProductVariationService` /
   `ProductVariationServiceImpl` (create/update/delete/find/findByProductId) are wired to a
   new `ProductVariationController` (`/api/v1/product-variation`, see HTTP API section above),
   with a `ProductVariationDto`, `ProductVariationHttpMapper`, and
   `ProductVariationApplicationService`. Deliberately left out of scope: no `AttributeDto`/
   attachment editing was added — `ProductVariationDto` covers only the variation's own
   scalar/FK fields, not the `attributes` (miswired join column, Gotcha #10) or `attachments`
   (backed by the dead `Attachment` entity, Gotcha #12) collections, both of which were
   already-flagged issues independent of this fix.
7. ~~Medium — stale cache on review writes.~~ Fixed (issue #35). `ProductReviewServiceImpl.save`
   (used via `ReviewApplicationService.createReview`), `update`, and `delete` now all evict
   `CacheConstants.PRODUCT_REVIEW_BY_PRODUCT_ID` (`allEntries = true`).
   ~~`PRODUCT_REVIEW_BY_USER_ID`~~ was declared in `CacheConstants` but never referenced by
   any `@Cacheable`/`@CacheEvict` — removed as a dead constant (issue #35/#51).
8. ~~Medium — `ProductServiceImpl.save` (create) never evicts `product_by_id`.~~ Fixed
   (issue #35) — `save` now evicts `product_by_id` (`allEntries = true`), same as
   `update`/`delete`.
9. ~~Medium — inconsistent/dead JPQL repository methods.~~ Fixed (issue #51).
   `ProductVariationRepository` and `ProductReviewRepository` each declared a
   `Page<...> findByProductId(UUID, Pageable)` overload that was never called from any
   service or controller — no pagination is actually exposed over HTTP anywhere in this
   module. Both dead overloads were removed. (There is still no search/filter
   functionality — by name, category, price, etc. — despite `Product`/`ProductVariation`
   having natural search fields; that part of the observation still stands.)
10. **Medium — miswired `@OneToMany` join column.** `ProductVariationEntity.java:58-60`:
    `attributes` is mapped `@OneToMany @JoinColumn(name = "category_id", nullable = false)`
    against `AttributeEntity` — `category_id` is a copy-paste leftover (there is no
    `category_id` concept on attributes/variations); the FK should reference the owning
    product variation. With `ddl-auto=update` this silently creates an ill-named
    `category_id` column on the `attribute` table.
11. **Medium — table-naming collision/confusion.** `CategoryEntity` maps to table `categry`
    (`CategoryEntity.java:18`, misspelled), while `ProductEntity`'s `@ManyToMany` join table
    for products↔categories is separately named `category` (`ProductEntity.java:40`,
    `ProductEntity.java:38-44`). Two different tables with near-identical, easily-confused
    names, neither of which is the "obvious" one for the other's purpose.
12. **Medium — dead `Attachment` entity.** `infra/models/Attachment.java` is a standalone
    `@Entity` in a package (`infra`) that is unrelated to the module's `infastructure`
    package. It is never referenced by any repository, service, or the
    `ProductVariation.attachments` (`List<UUID>`) field it appears intended to back. With
    `ddl-auto=update` it still creates an orphan `attachment` table in the database.
13. **Medium — `Inventory.reservedQuantity` is fully plumbed but never written.** The field
    exists on the entity, domain model, and DTO, and `getAvailableQuantity()` (domain and
    DTO) computes `quantity - reservedQuantity`, but no code path anywhere increments or
    decrements `reservedQuantity` — it is permanently `0` outside of what a test or manual
    DB write sets. `checkAvailability` also only compares against `quantity`, not
    `getAvailableQuantity()`, making the "reserved" concept entirely inert.
14. **Medium — duplicate stock field.** `ProductVariation`/`ProductVariationEntity` carries
    its own `stockQuantity` in addition to the separate `Inventory.quantity` row keyed by
    `productVariationId`. Nothing in this module keeps them in sync — `deductStock`/
    `restoreStock` only touch `InventoryEntity.quantity`; `stockQuantity` is set once at
    variation creation and never updated by the inventory operations, so the two numbers
    can diverge.
15. **Medium — hardcoded HTTP paths bypass `ApiPaths`.** `CategoryController.java:16`
    (`/api/v1/categories`) and `ProductReviewController.java:17`
    (`/api/v1/productReview`) hardcode their base paths instead of using constants in
    `ApiPaths.java`, which only defines `PRODUCT_BASE` and the inventory paths — contrary
    to the repo-wide convention documented in the root `CLAUDE.md`.
16. **Low — `InventoryMapper.toEntity` drops `productVariation`.** `InventoryMapper.java:26-35`
    builds an `InventoryEntity` without setting the required (`nullable = false`)
    `productVariation` association; any code path relying on this particular mapper method
    (currently unused by `InventoryServiceImpl`, which builds `Inventory` by hand) would
    fail to persist correctly.
17. **Low — `ProductReviewEntity` timestamps never populated.** Unlike every other entity
    with `createdAt`/`updatedAt`, `ProductReviewEntity` has no `@CreationTimestamp`/
    `@UpdateTimestamp` (`ProductReviewEntity.java:37-41`), and no service/mapper ever sets
    these fields manually (`ProductReviewMapper.toEntity` and `ReviewApplicationService`
    both omit them) — reviews are persisted with `NULL` timestamps. The entity's Java field
    for the update timestamp is also named `updated_at` (snake_case), inconsistent with
    every other entity's camelCase `updatedAt`.
18. **Low — inconsistent `CacheConstants` naming.** `CacheConstants.java`: `product_by_id`
    is lower_snake_case while the other three constants are `UPPER_SNAKE_CASE`; the class
    declaration itself is split across two lines (`public final class` / `CacheConstants {`)
    — cosmetic but indicates the file was not reviewed carefully.
19. **Low — misleading Dockerfile comment.** `Dockerfile:34` states "Port is dynamically
    assigned (server.port=0 in application.properties)" but the shipped
    `src/main/resources/application.yml` sets `server.port: 8080`; `server.port: 0` only
    appears in the test profile (`src/test/resources/application-test.yml`), which the
    Docker image never uses.
20. ~~Low — vestigial Spring Cloud Config/Eureka bootstrap.~~ Removed (issue #50).
    `bootstrap.properties` configured `spring.cloud.config.*` and
    `eureka.client.serviceUrl.defaultZone` pointing at a `config-server`/`naming-server`
    that never existed in this repo (`application.yml` set
    `spring.cloud.config.enabled=false` and `discovery.enabled=false`, making it dead
    configuration carried over from an earlier architecture).
21. **Naming deviations (intentional, not to be "fixed"):** package `infastructure/`
    (misspelled, should be `infrastructure/`) and `dataAccess/Dao/` (capital `D`) — called
    out in the root `CLAUDE.md` as known, deliberate deviations from the four-layer
    convention used by other modules. Do not rename; the rename would touch imports across
    the whole module.
