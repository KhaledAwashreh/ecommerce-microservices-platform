# order-service

## Purpose

`order-service` owns carts, orders, order items, and discounts for the e-commerce
platform. It exposes a REST API for order CRUD and lookup (`OrderController`), and holds
a `CartService`/`CartServiceImpl` domain layer for cart manipulation that currently has
**no HTTP entry point** (see Gotchas). Its main orchestration responsibility is creating
an order: validate stock via `product-service` (over Feign, through the API gateway),
persist the order as `PENDING`, deduct inventory, then flip to `CONFIRMED` — or mark the
order `CANCELLED` if the deduction call fails. It also declares (but does not use) Feign
clients for `payment-service` and `user-service`, and Redis-backed cache infrastructure
that no code in the module actually invokes.

## Package layout

```
order_service/
├── OrderServiceApplication.java          @SpringBootApplication, @EnableFeignClients(basePackages=order_service)
├── application/
│   ├── controller/OrderController.java   REST endpoints for orders (no cart controller)
│   ├── dto/                              CartDto, CartItemDto, OrderDto, OrderItemDto, DiscountDto
│   └── mapper/                           CartHttpMapper, OrderHttpMapper (domain <-> DTO)
├── constants/ApiPaths.java               Order paths + external Feign path fragments
├── dataAccess/
│   ├── entity/                           CartEntity, CartItemEntity, OrderEntity, OrderItemEntity, DiscountEntity (JPA)
│   ├── mapper/                           CartMapper, CartItemMapper, OrderMapper, OrderItemMapper, DiscountMapper (entity <-> domain)
│   └── repository/                       CartRepository, CartItemRepository, OrderRepository (Spring Data JPA)
├── domain/
│   ├── enums/                            CartStatus, OrderStatus
│   ├── exception/                        InsufficientStockException, ProductServiceException
│   ├── model/                            Cart, CartItem, Order, OrderItem, Discount (plain POJOs)
│   └── service/ + service/impl/          CartService/CartServiceImpl, OrderService/OrderServiceImpl
└── infrastructure/
    ├── cache/CacheConfig.java            RedisCacheManager, RedisTemplate, StringRedisTemplate beans (unused — no @Cacheable anywhere)
    ├── config/FeignClientConfig.java     Feign logger level + productServiceErrorDecoder bean
    └── http/
        ├── client/                       ProductServiceClient, PaymentClient, UserServiceClient, ProductServiceErrorDecoder
        └── dto/                          ProductDto, InventoryDto, PaymentDto, UserDto, CategoryDto (Feign response shapes)
```

No `dataAccess/dao/` — repositories are plain Spring Data interfaces. Package casing
follows the repo convention (`dataAccess`, not `dataaccess`/`infastructure`).

## Domain model

| Domain (POJO) | Entity | Notable fields | Notes |
|---|---|---|---|
| `Cart` | `CartEntity` (`@Table("cart")`) | `id, userId, createdBy, updatedBy, sessionId, status, cartItems, subtotal, discountTotal, taxTotal, shippingTotal, totalPrice, createdAt, updatedAt` | Domain model has `createdBy`/`updatedBy`; entity has them too. `@Component`-annotated domain POJO (see Gotchas). |
| `CartItem` | `CartItemEntity` (`@Table("cart_item")`) | `id, cartId, productId, productVariantId, storeId, productSku, productName, quantity, unitPrice, lineTotal, currency, createdAt, updatedAt` | Entity `cart` FK is `optional=false`/`nullable=false`; domain model only carries `cartId` (a UUID), not the parent reference. |
| `Order` | `OrderEntity` (`@Table("\"order\"")`, quoted because `order` is a SQL keyword) | `id, storeId, seller, buyer, status, selectedItems, discountsApplied, createdAt, updatedAt, createdBy, updatedBy` | `selectedItems`/`discountsApplied` setters are hand-written (Lombok `@Setter(AccessLevel.NONE)` + custom methods) to defensively copy into new `ArrayList`s. |
| `OrderItem` | `OrderItemEntity` (`@Table("order_item")`) | `id, orderId, productSku, quantity, unitPrice, createdAt, updatedAt, createdBy, updatedBy` | `productSku` is typed `UUID`, not a SKU string — see Gotchas. |
| `Discount` | `DiscountEntity` (`@Table("discount")`) | `id, name, code (unique), description, createdAt, updatedAt, createdBy, updatedBy` | Joined to `Order` via `order_discount` join table (`@ManyToMany`, cascade `PERSIST`/`MERGE`). |

Mapping is one-directional per layer: `dataAccess/mapper/*Mapper` converts entity <->
domain; `application/mapper/*HttpMapper` converts domain <-> DTO. All mapper classes are
`final` with private/no constructor and static methods only.

### CartStatus (`domain/enums/CartStatus.java`)

```
ACTIVE, CHECKOUT_IN_PROGRESS, CONVERTED, ABANDONED
```

No transition logic exists anywhere in the module — `CartEntity.status` defaults to
`ACTIVE` (`@Builder.Default`) and can be set to any value via `CartService.update()` /
`CartMapper.toEntity`. There is no state machine, no guard against illegal transitions
(e.g. `CONVERTED` -> `ACTIVE`), and no code that ever sets `CHECKOUT_IN_PROGRESS`,
`CONVERTED`, or `ABANDONED` at all — those three values are unreachable from any code
path in this module.

### OrderStatus (`domain/enums/OrderStatus.java`)

```
PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
```

Only `PENDING`, `CONFIRMED`, and `CANCELLED` are ever assigned by code (in
`OrderServiceImpl.create`/`createOrderFromCart`). `SHIPPED` and `DELIVERED` are declared
but nothing in this module ever transitions an order into them — they are set-once-never
values as far as `order-service` is concerned (some other service or manual DB update
would be required). There is no enforcement preventing, e.g., `OrderController.updateOrder`
from setting an arbitrary status via `PUT /api/v1/orders/{id}` (see HTTP API / Gotchas).

## Persistence

- **Schema source**: Hibernate `ddl-auto: update` in `application.yml` (dev/docker), `create-drop`
  in the test profile (`src/test/resources/application-test.yml`). No Flyway/Liquibase — the
  schema is entirely generated from JPA annotations. No SQL migration files exist in the module.
- **Database**: PostgreSQL, database name `orderdb` (`spring.datasource.url` in every profile).
- Tables: `cart`, `cart_item`, `"order"` (quoted — `order` is a reserved word), `order_item`,
  `discount`, and the implicit join table `order_discount` (`order_id`, `discount_id`).
- `CartEntity.cartItems` and `OrderEntity.selectedItems` are `@OneToMany(cascade = ALL,
  orphanRemoval = true, fetch = LAZY)`, mapped by `cart`/`order` respectively.
- Repositories (`order-service/src/main/java/.../dataAccess/repository/`):
  - `CartRepository`: `findByUserId`, `findBySessionId`, `findByStatus`, plus JPQL
    `findByUserIdAndStatus` / `findBySessionIdAndStatus`.
  - `CartItemRepository`: `findByCartId`, `findByIdAndCartId`, `deleteByCartId`, plus JPQL
    `findByCartIdAndStoreId` / `findByCartIdAndProductId` — **both JPQL finder methods are
    never called anywhere in the module** (dead queries).
  - `OrderRepository`: `findByBuyer`, `findBySeller`, `findByStoreId`, `findByStatus`, plus
    JPQL `findByBuyerAndStoreId` / `findBySellerAndStoreId` / `findByBuyerAndStatus`.

## HTTP API

Base path: `ApiPaths.ORDER_BASE = /api/v1/orders`. All defined in
`application/controller/OrderController.java`. There is **no cart controller** — `CartService`
exists and is fully implemented but is not wired to any `@RestController`, so none of its
operations (create cart, add/remove/update item, clear cart, recalc totals) are reachable
over HTTP in this module.

| Method | Path | Request body | Response | Status codes | Auth |
|---|---|---|---|---|---|
| POST | `/api/v1/orders` | `OrderDto` | `OrderDto` | 201 Created (or 500 on any unhandled exception — no `@ControllerAdvice` in this module) | None enforced in-module; relies on upstream gateway/JWT filter |
| GET | `/api/v1/orders` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/{id}` | — | `OrderDto` or empty body | 200, 404 if not found | none |
| GET | `/api/v1/orders/buyer/{buyerId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/seller/{sellerId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/status/{status}` | — (path enum `OrderStatus`) | `List<OrderDto>` | 200, 400 if `status` doesn't parse to the enum | none |
| GET | `/api/v1/orders/buyer/{buyerId}/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/seller/{sellerId}/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/buyer/{buyerId}/status/{status}` | — | `List<OrderDto>` | 200 | none |
| PUT | `/api/v1/orders/{id}` | `OrderDto` | `OrderDto` | 200 (or 500 — `update()` has no not-found guard, throws if the underlying save produces a detached/transient conflict) | none |
| DELETE | `/api/v1/orders/{id}` | — | empty | 204 (204 even if `id` doesn't exist — `deleteById` on Spring Data throws `EmptyResultDataAccessException` in that case, uncaught -> would actually surface as 500) | none |

`createOrder` does not call `@Valid` — `spring-boot-starter-validation` is a declared
dependency (`pom.xml`) but no `@Valid`/`@Validated` annotation appears anywhere in the
module, and none of the request DTOs are validated at the HTTP boundary; Lombok's
`@NonNull` on DTO fields only guards the generated all-args constructor/builder, not
JSON deserialization, so a POST body missing a `@NonNull` field silently binds `null`.

`updateOrder` lets the caller set `status` to any `OrderStatus` value (including going
backward, e.g. `CONFIRMED` -> `PENDING`, or jumping to `SHIPPED`/`DELIVERED` without ever
going through the create flow) — there is no transition guard.

No `@ControllerAdvice`/`GlobalExceptionHandler` exists in `order-service` (confirmed by
grep across `src/main`), unlike the pattern described in the root `CLAUDE.md`
("Errors surface through `GlobalExceptionHandler` using `common`'s `ErrorResponse`").
Exceptions thrown by the service layer (`IllegalArgumentException`,
`InsufficientStockException`, `RuntimeException` from the compensating-transaction path)
propagate to Spring Boot's default `/error` handling and produce the default Spring error
JSON, not `common.dto.ErrorResponse`.

## Order creation orchestration (`OrderServiceImpl.create`)

`order-service/src/main/java/.../domain/service/impl/OrderServiceImpl.java:37-60`

```
@Transactional(rollbackFor = Exception.class)
create(order):
    1. validateInventoryAvailability(order)     // remote Feign calls, no DB writes yet
    2. entity = toEntity(order); entity.status = PENDING
    3. saved = repository.save(entity)          // INSERT within the current transaction
    4. try:
         updateProductInventory(order)          // remote Feign deduct call(s)
         saved.status = CONFIRMED
         repository.save(saved)                 // UPDATE within the same transaction
         return saved
       catch (Exception e):
         saved.status = CANCELLED
         repository.save(saved)                 // UPDATE within the same transaction
         throw new RuntimeException(...)         // re-thrown — propagates out of the @Transactional method
```

### Step 1 — `validateInventoryAvailability`

For every `OrderItem` in `order.getSelectedItems()`:
1. `productServiceClient.retrieveProduct(item.getProductSku())` — 404/null -> `IllegalArgumentException`.
2. `productServiceClient.retrieveInventory(item.getProductSku())` — null -> `IllegalArgumentException`.
3. Compares `inventory.getAvailableQuantity()` (`quantity - reservedQuantity`,
   `infrastructure/http/dto/InventoryDto.java:31-33`) against `item.getQuantity()`; throws
   `InsufficientStockException` if insufficient.
4. Any other exception from the Feign calls is caught and rewrapped as
   `IllegalArgumentException("Unable to validate product availability: " + msg)`.

This step makes **no database writes** — it is pure validation, called before the first
`repository.save()`.

### Step 2/3 — persist PENDING

The order (with all `OrderItem`s, back-referenced via
`entity.getSelectedItems().forEach(item -> item.setOrder(entity))`) is mapped to
`OrderEntity` and saved with `status = PENDING`. This save happens **inside the same
`@Transactional(rollbackFor = Exception.class)` method** as the subsequent inventory
deduction and the compensating update.

### Step 4 — deduct inventory, confirm or compensate

`updateProductInventory` calls, per item:
1. `productServiceClient.deductInventory(item.getProductSku(), item.getQuantity())` —
   if it returns `false` (not an exception, just a falsy boolean), a `RuntimeException` is
   thrown.
2. On success it re-fetches the product (`retrieveProduct`, a second, redundant Feign call
   already made once in step 1) purely to log its id — the returned `ProductDto` is not
   otherwise used. If this second lookup returns `null`, a `RuntimeException` is thrown
   **after** the deduction already succeeded — see Gotchas (no restore on this path either).
3. Any exception here is caught and rewrapped/rethrown as `RuntimeException("Inventory
   update failed for product " + sku + " - distributed transaction will be rolled back", e)`.

If `updateProductInventory` succeeds: `saved.status = CONFIRMED`, saved again, returned.

If it throws: the code sets `saved.status = CANCELLED` and calls `repository.save(saved)`,
then re-throws a new `RuntimeException` wrapping the original cause.

### What "compensating" actually means here

**There is no inventory-restore call anywhere in this flow.** `ProductServiceClient`
declares `restoreInventory(UUID productVariationId, int quantity)`
(`infrastructure/http/client/ProductServiceClient.java:33-36`, backed by
`ApiPaths.INVENTORY_RESTORE = /api/v1/inventory/product-variation/{id}/restore`), but it
is **never invoked from any code in this module** (confirmed by grep across `src/main`).
The only "compensation" performed is a local DB status flip to `CANCELLED` — deducted
stock is not restored via any call to `product-service`. If `deductInventory` returns
`true` for item 1 of a multi-item order and then fails validating/deducting item 2, the
order is marked `CANCELLED` locally but item 1's stock deduction on `product-service` is
never reversed. This is a real inventory-leak bug, not merely undocumented behavior.

### Transaction boundary vs. remote calls — the CANCELLED save may not persist

`create()` is annotated `@Transactional(rollbackFor = Exception.class)` at the method
level. The method throws a `RuntimeException` on the failure path (both from within the
`catch` block and propagated from `updateProductInventory`'s own re-throws). Because that
exception escapes the transactional proxy boundary, Spring's transaction interceptor
rolls back the **entire transaction**, including the `saved.status = CANCELLED` update
issued just before the throw, and — because the initial `PENDING` insert
(`repository.save(entity)` in step 3) is in the *same* transaction — that insert is rolled
back too. In practice, on the failure path **no order row survives at all**: the intended
`CANCELLED` audit record is never actually persisted, despite the code appearing to write
it. This can only be confirmed by tracing the `@Transactional` propagation; it is not
visible from a superficial read of the method body. `createOrderFromCart`
(`OrderServiceImpl.java:220-247`) has the identical structure and the identical bug.

Remote Feign calls to `product-service` (steps 1 and 4) execute *inside* this same local
DB transaction's scope — a slow or failing remote call holds the DB transaction/connection
open for the duration (`connectTimeout: 5000`, `readTimeout: 10000` in `application.yml`),
and a JVM crash or timeout between "inventory deducted" and "order confirmed" leaves
`product-service` stock deducted with no corresponding confirmed order in `order-service`
at all (transaction rolled back per above) — a genuine distributed-transaction hazard the
code's own comments ("distributed transaction rolled back") acknowledge without actually
solving.

### `createOrderFromCart` (dead/unreachable public method)

`OrderServiceImpl.createOrderFromCart(UUID cartId, UUID buyer)` duplicates the entire
`create()` orchestration but sources the `Order` from `convertCartToOrder`, which **does
not read the cart's items at all** — it builds an `Order` with `buyer`, `status=PENDING`,
timestamps, and an **empty `selectedItems` list**; the `cartId` parameter is only used for
logging. This method is not declared on the `OrderService` interface, so it is
unreachable via the `OrderService` abstraction used everywhere else (`OrderController`,
tests); it could currently only be invoked by code with a concrete
`OrderServiceImpl` reference, and no such caller exists in the module. Combined with the
empty-items bug, calling it would also immediately fail
`validateInventoryAvailability`'s check for an empty list... except that check is never
reached because — wait: `validateInventoryAvailability` throws
`IllegalArgumentException("Order must contain at least one item")` only if
`selectedItems == null || isEmpty()`, so `createOrderFromCart` would always throw at that
point if actually called. This method and `convertCartToOrder` are dead, broken, and
disconnected from `CartService`/`CartRepository`/`CartItemRepository` (none are injected
into `OrderServiceImpl`).

## Outbound dependencies (Feign clients)

`@EnableFeignClients(basePackages = "com.kawashreh.ecommerce.order_service")` in
`OrderServiceApplication`.

| Client | `@FeignClient` name | Target base path | Used by | Notes |
|---|---|---|---|---|
| `ProductServiceClient` | `product-service` | `/api/v1/product`, `/api/v1/inventory` | `OrderServiceImpl` (`retrieveProduct`, `retrieveInventory`, `deductInventory`) | `checkInventoryAvailability` and `restoreInventory` are declared but **never called** anywhere in `src/main`. |
| `PaymentClient` | `payment-service` | `/api/v1/payment` | **nothing** — not injected/called anywhere in `src/main` | Fully dead client; declared, configured (`application.yml` has a `payment-service` Feign client config block), never used. |
| `UserServiceClient` | `user-service` | `/api/v1/user/{userId}` | **nothing** — not injected/called anywhere in `src/main` | Fully dead client; same situation as `PaymentClient`. |

- **Target URL resolution**: `application.yml` (base profile) points all three Feign
  client names at `${GATEWAY_URL:http://api-gateway:8765}` — i.e. order-service calls
  through the API gateway by default, per the root `CLAUDE.md` convention. The k8s
  `order-configmap.yaml` overrides this per-service to `http://product-service`,
  `http://user-service`, `http://payment-service` (direct in-cluster DNS, bypassing the
  gateway) — a real behavioral difference between the Docker Compose/local profile and
  the Kubernetes deployment, not just a URL substitution.
- **Failure handling**:
  - `feign.circuitbreaker.enabled: true` (`application.yml`) routes Feign calls through
    Resilience4j.
  - `resilience4j.circuitbreaker.instances.product-service`: `slidingWindowSize: 10`,
    `minimumNumberOfCalls: 5`, `permittedNumberOfCallsInHalfOpenState: 3`,
    `waitDurationInOpenState: 5s`, `failureRateThreshold: 50`. No circuit breaker instance
    is configured for `payment-service` or `user-service` (moot, since neither client is
    ever called).
  - `resilience4j.retry.instances.product-service`: `maxAttempts: 3`, `waitDuration: 1s`.
    No retry instance configured for the other two clients.
  - No `@CircuitBreaker`/`@Retry` annotations appear anywhere in `src/main` — the
    Resilience4j config applies automatically at the Feign-client level because
    `feign.circuitbreaker.enabled=true` wraps every Feign client method, keyed by the
    `@FeignClient` name (`product-service`). There is no fallback method/class configured,
    so an open circuit or exhausted retries on `product-service` simply propagates the
    underlying (or a `CallNotPermittedException`) exception up through
    `validateInventoryAvailability`'s catch-all, rewrapped as `IllegalArgumentException`.
  - `feign.client.config.default`: `connectTimeout: 5000`, `readTimeout: 10000`,
    `loggerLevel: basic`. `feign.client.config.product-service.errorDecoder:
    productServiceErrorDecoder` wires `ProductServiceErrorDecoder`
    (`infrastructure/http/client/ProductServiceErrorDecoder.java`) only for the
    `product-service` client.
  - `ProductServiceErrorDecoder` maps HTTP 404/400/503 (and a default case for everything
    else) to `ProductServiceException` with a status code and message. It does **not**
    apply to `PaymentClient` or `UserServiceClient` (unused anyway, and not configured with
    any error decoder — they'd fall back to Feign's `ErrorDecoder.Default`).
  - `extractProductIdFromMethodKey` in `ProductServiceErrorDecoder` always returns the
    literal string `"unknown"` — it does not actually parse the method key despite the
    comment claiming it's "for logging purposes"; the resulting `ProductServiceException`
    always carries `productId = "unknown"`.
  - `FeignClientConfig` (`infrastructure/config/FeignClientConfig.java`) registers
    `feignLoggerLevel()` (BASIC) and the `productServiceErrorDecoder` bean globally, but is
    not referenced via `@FeignClient(configuration = ...)` on any client — it relies on
    Spring Cloud OpenFeign's default context picking up beans by name/type, combined with
    the YAML `errorDecoder: productServiceErrorDecoder` reference.

## Configuration

| Property | Default / value | Source | Notes |
|---|---|---|---|
| `spring.application.name` | `order-service` | `application.yml` | |
| `server.port` | `8080` | `application.yml` | `application-ide.yml` overrides to `8083`. Dockerfile comment claims "Port is dynamically assigned (server.port=0 in application.properties)" — that is only true for `src/test/resources/application-test.yml` (`server.port: 0`), **not** for the shipped `application.yml`; the Dockerfile comment is stale/incorrect for the running image. |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/orderdb` (base) | `application.yml`, overridden per profile (`-local`: `localhost:5433`, `-ide`: `localhost:5435`, k8s configmap: `postgres-server:5432`, test: `localhost:5432` via Testcontainers-injected URL) | |
| `spring.datasource.username` / `password` | `postgres` / `test1234` | `application.yml` | Dev-only credential per root `CLAUDE.md` warning. |
| `spring.jpa.hibernate.ddl-auto` | `update` (main), `create-drop` (test) | `application.yml`, `application-test.yml` | |
| `spring.jpa.show-sql` | `true` | `application.yml`, `application-test.yml` | |
| `spring.data.redis.host` / `port` | `localhost:6379` (base default), `redis:6379` (`-local` profile — see Gotchas), `redis-server:6379` (k8s) | `application.yml`, `application-local.yml`, configmap | |
| `GATEWAY_URL` | `http://api-gateway:8765` (base default) | `application.yml`; `application-ide.yml` sets literal `http://localhost:8765` | |
| `feign.circuitbreaker.enabled` | `true` | `application.yml` | |
| `feign.client.config.default.connectTimeout` / `readTimeout` | `5000` / `10000` (ms) | `application.yml` | k8s configmap's embedded `application.yml` overrides `readTimeout` to `5000` for its own `default` client config block. |
| `feign.client.config.product-service.errorDecoder` | `productServiceErrorDecoder` | `application.yml` | |
| `resilience4j.circuitbreaker.instances.product-service.*` | see Outbound dependencies | `application.yml` | |
| `resilience4j.retry.instances.product-service.*` | see Outbound dependencies | `application.yml` | |
| `management.zipkin.tracing.endpoint` | `http://zipkin:9411/api/v2/spans` (base), `http://localhost:9411/...` (`-ide`) | `application.yml`, `application-ide.yml`, `application-local.yml` | |
| `management.tracing.sampling.probability` | `1.0` | `bootstrap.properties` | |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | `bootstrap.properties`, re-set in `application-local.yml` | |
| `spring.cloud.config.*`, `eureka.client.*` | config-server / naming-server URLs | `bootstrap.properties` | Legacy Spring Cloud Config/Eureka bootstrap wiring; the k8s configmap explicitly sets `eureka.client.enabled: false`, `register-with-eureka: false`, `fetch-registry: false`, suggesting Eureka discovery is not actually used in the deployed topology even though `bootstrap.properties` configures a `defaultZone`. |

`application-local.yml`'s doc comment says "For running from IDE without Docker", but it
points `spring.data.redis.host` at `redis` (a Docker Compose service hostname, not
`localhost`) — inconsistent with its own stated purpose and with `application-ide.yml`
(which is the profile that actually uses `localhost` throughout and is explicitly headed
"run order-service in IDE, everything else in Docker").

## Caching

`infrastructure/cache/CacheConfig.java` declares `@EnableCaching` and three beans:
`RedisCacheManager` (10-minute TTL, JSON serialization via
`GenericJackson2JsonRedisSerializer` with `JavaTimeModule` registered, null-value caching
disabled, `transactionAware()`), a generic `RedisTemplate<String, Object>`, and a
`StringRedisTemplate`. **No cache name is ever defined, and no `@Cacheable`/`@CacheEvict`/
`@CachePut` annotation exists anywhere in `src/main`** (confirmed by grep). The Redis
dependency (`spring-boot-starter-data-redis`) and this configuration class are therefore
present but functionally inert in this module today — infrastructure for caching that
nothing in the module currently uses. `CacheConstants` (referenced generically in root
`CLAUDE.md`) does not exist in this module.

## Security

No security configuration, filters, or `@PreAuthorize`/`@Secured` annotations exist in
`order-service`. Per root `CLAUDE.md`, auth (JWT) is expected to be validated upstream at
`api-gateway`; `order-service` itself performs no token validation, no auth check, and no
identity-header propagation logic — it trusts whatever caller reaches it (any header-based
identity forwarding, if it happens, is entirely the gateway's responsibility and is
invisible from this module's code).

## Tests

- `src/test/java/.../OrderServiceIntegrationTest.java` — the only test class in the
  module. `@SpringBootTest` + `@Testcontainers` + `@ActiveProfiles("test")`, backed by a
  real `PostgreSQLContainer` (`postgres:16-alpine`) wired via `@DynamicPropertySource`
  (overrides datasource URL/credentials and forces `ddl-auto=create-drop`).
  `ProductServiceClient` is a `@MockitoBean` (mocked Feign client) — no real HTTP call to
  `product-service` occurs in tests, and no WireMock/stub server is used.
  - Covers: successful order creation (`CONFIRMED`), insufficient-inventory failure path
    (asserts message contains "Insufficient stock" — but note: since the code is wrapped
    in try/catch inside the test rather than `assertThrows`, if `orderService.create`
    *doesn't* throw, the test silently passes with no assertion executed — same pattern in
    `create_shouldFail_whenProductNotFound`), product-not-found failure path, `findByBuyer`,
    `findByStatus` (asserts a `CANCELLED` filter returns empty after a successful create).
  - **Not covered**: `CartService`/`CartServiceImpl` (zero test coverage for cart create,
    add/remove/update item, clear, recalc totals, or any `CartStatus` transition);
    `OrderController` (no `@WebMvcTest`/`MockMvc` test exists — no endpoint, path
    variable, or status-code coverage); the compensating-cancel path (no test forces
    `deductInventory` to fail/return `false` to observe the `CANCELLED` transition or the
    transaction-rollback behavior documented above); `PaymentClient`/`UserServiceClient`
    (both entirely unused, so untested); Resilience4j circuit-breaker/retry behavior;
    `ProductServiceErrorDecoder`; caching (none exists to test).
- Run: `mvn -pl order-service test` (requires a running Docker daemon for Testcontainers,
  per root `CLAUDE.md`).

## Gotchas

1. **No inventory-restore compensation call** — `ProductServiceClient.restoreInventory`
   is declared and routed (`ApiPaths.INVENTORY_RESTORE`) but never invoked. The
   "compensating transaction" only flips the local order status; deducted stock on
   `product-service` is never reversed on failure.
   `order-service/src/main/java/.../domain/service/impl/OrderServiceImpl.java:106-133,
   47-59`.
2. **The CANCELLED save is itself rolled back** — `create()`/`createOrderFromCart()` are
   `@Transactional(rollbackFor = Exception.class)`; the failure branch re-throws a
   `RuntimeException` after saving `CANCELLED`, so Spring rolls back the whole method's
   transaction, including the original `PENDING` insert and the `CANCELLED` update. No
   order row survives a failed creation despite the code appearing to persist a
   `CANCELLED` record. `OrderServiceImpl.java:37-60, 220-247`.
3. **No cart HTTP endpoints** — `CartService`/`CartServiceImpl` is fully implemented but
   has no `@RestController`. All cart functionality (create, add/remove/update item, clear,
   recalc totals) is unreachable over HTTP in this module.
   `order-service/src/main/java/.../application/controller/` (no `CartController.java` present).
4. **`CartMapper.toEntity` never sets the `CartItemEntity.cart` back-reference** — unlike
   `OrderServiceImpl.create`, which manually does `item.setOrder(entity)` before saving,
   `CartServiceImpl.update()` calls `CartMapper.toEntity(cart)` and saves directly with no
   equivalent back-reference wiring. Given `CartItemEntity.cart` is `nullable=false,
   optional=false`, saving a `Cart` with existing items through `update()` risks a
   constraint violation / persistence failure.
   `order-service/src/main/java/.../dataAccess/mapper/CartMapper.java:10-28`,
   `domain/service/impl/CartServiceImpl.java:146-151`.
5. **`PaymentClient` and `UserServiceClient` are fully dead code** — declared, configured
   in `application.yml`'s Feign client config block, but never injected or called by any
   class in `src/main`. `infrastructure/http/client/PaymentClient.java`,
   `infrastructure/http/client/UserServiceClient.java`.
6. **`ProductServiceClient.checkInventoryAvailability` is declared and never called** —
   `validateInventoryAvailability` re-implements the same check manually via
   `retrieveInventory` instead of calling this endpoint.
   `infrastructure/http/client/ProductServiceClient.java:23-26`.
7. **`createOrderFromCart` is dead and broken** — not on the `OrderService` interface (so
   unreachable through normal DI), and its helper `convertCartToOrder` never reads the
   cart's items (`selectedItems` stays empty), so calling it would always immediately fail
   `validateInventoryAvailability`'s empty-list check. `CartRepository`/`CartItemRepository`
   are not injected into `OrderServiceImpl` at all. `OrderServiceImpl.java:220-263`.
8. **Redundant duplicate Feign calls to `retrieveProduct`** — called once in
   `validateInventoryAvailability` and again in `updateProductInventory` for the same item,
   purely to log the product id a second time. `OrderServiceImpl.java:69, 117`.
9. **Duplicate log line** — the two `logger.info("Inventory validation passed...")` calls
   at `OrderServiceImpl.java:92-95` log functionally the same message twice per item (once
   keyed by SKU, once by product id).
10. **No `@ControllerAdvice`/`GlobalExceptionHandler` in this module** — contrary to the
    pattern the root `CLAUDE.md` describes repo-wide, unhandled `IllegalArgumentException`,
    `InsufficientStockException`, and the wrapped `RuntimeException` from `create()` all
    surface as Spring Boot's default error response, not `common.dto.ErrorResponse`.
11. **No request-body validation** — `spring-boot-starter-validation` is a pom dependency
    but `@Valid`/`@Validated` is never used; `OrderController.createOrder`/`updateOrder`
    accept any `OrderDto`, including one with `null` `@NonNull`-annotated fields (Lombok's
    `@NonNull` is not enforced during JSON deserialization).
12. **`updateOrder`/`deleteOrder` have no not-found guard** — `update()` calls
    `repository.save()` unconditionally (no existence check), and `delete()` calls
    `repository.deleteById(id)` directly, which throws `EmptyResultDataAccessException`
    (uncaught, surfaces as 500) if `id` does not exist, rather than a 404.
    `OrderController.java:96-110`, `OrderServiceImpl.java:207-218`.
13. **`OrderItem.productSku` / `CartItem.productSku` are typed `UUID`, not a SKU string** —
    despite the name, the field is used interchangeably as a product id
    (`retrieveProduct`) and a product-variation id (`retrieveInventory`,
    `deductInventory`), which is confusing given `ProductDto` separately has both an
    `id` and (elsewhere in the platform) a distinct notion of variation.
    `domain/model/OrderItem.java:25`, `domain/model/CartItem.java:27`.
14. **Domain POJOs annotated `@Component`** — `Cart`, `OrderItem`, `Discount` (but not
    `CartItem` or `Order`) carry Spring's `@Component` stereotype despite being plain
    data-holder POJOs built via Lombok `@Builder`/manual `new`, never retrieved from the
    Spring context as beans anywhere in the module. Inconsistent even within the domain
    model package (`CartItem`/`Order` do *not* have `@Component`).
    `domain/model/Cart.java:19`, `domain/model/OrderItem.java`, `domain/model/Discount.java`.
15. **Stale Dockerfile comment** — `order-service/Dockerfile:34` claims `server.port=0`
    (dynamic port assignment) is active for the built image; that is only true of the test
    profile. The shipped `application.yml` fixes `server.port: 8080`, and the Dockerfile
    doesn't set `SPRING_PROFILES_ACTIVE=test`.
16. **`application-local.yml` Redis host contradicts its own doc comment** — comment says
    "For running from IDE without Docker" but sets `spring.data.redis.host: redis` (a
    Compose service name, unreachable without Docker networking).
    `src/main/resources/application-local.yml:1-14`.
17. **`ProductServiceErrorDecoder.extractProductIdFromMethodKey` always returns
    `"unknown"`** — the method's own comment says it's "for logging purposes" but performs
    no extraction; every `ProductServiceException` built from a Feign error carries
    `productId = "unknown"` regardless of which product actually failed.
    `infrastructure/http/client/ProductServiceErrorDecoder.java:42-46`.
18. **Feign target URLs differ between Docker Compose/local profiles and Kubernetes** —
    base `application.yml` and `-ide` route all three Feign clients through the API
    gateway (`GATEWAY_URL`); the k8s `order-configmap.yaml` points them directly at
    `http://product-service`, `http://user-service`, `http://payment-service` in-cluster,
    bypassing the gateway entirely for service-to-service calls in that environment.
19. **CacheConfig is dead infrastructure** — Redis cache beans are fully configured
    (10-minute TTL, JSON serialization) but nothing in the module is `@Cacheable`; the
    entire caching layer is currently a no-op in terms of actual caching behavior.
20. **Two integration tests assert nothing on the "happy" branch of their own scenario** —
    `create_shouldFail_whenInsufficientInventory` and `create_shouldFail_whenProductNotFound`
    wrap `orderService.create(order)` in `try { ... } catch (Exception e) { assertThat(...) }`
    with no `fail()`/`Assertions.assertThrows` if no exception is thrown — if a future
    regression stops the code from throwing, these tests pass vacuously.
    `src/test/java/.../OrderServiceIntegrationTest.java:122-152, 154-178`.
