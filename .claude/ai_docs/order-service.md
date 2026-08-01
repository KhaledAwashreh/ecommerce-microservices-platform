# order-service

> **Amendment (GH #17 fix):** this doc predates the fix and the "No security
> configuration, filters, or `@PreAuthorize`/`@Secured` annotations exist... performs
> no token validation" statement is now **stale**. Current state:
> `infrastructure/security/JwtAuthFilter` + `JwtService` validate every request's
> bearer token locally (except `/actuator/**`) before it reaches a controller, using
> the same shared HMAC secret as user-service/api-gateway (`constants/JwtConstants`).
> A new `IncomingAuthHeaderFeignInterceptor` forwards the caller's Authorization
> header onto this service's outbound Feign calls to product-service, payment-service,
> and user-service (the application.yml comment about those calls going direct by DNS
> "with no interceptor to attach one" is no longer accurate). The rest of this
> document is otherwise still accurate as of the fix; it has not been fully
> regenerated.

> **Amendment (GH #42 fix):** the module now has an `exception/GlobalExceptionHandler`
> (`@RestControllerAdvice`), the first in this module. `OrderServiceImpl.update()`/
> `delete()` call `repository.existsById()` first and throw
> `common.exceptions.NoSuchElementException` for a missing id, which the handler maps
> to `404` with a `common.dto.ErrorResponse` body. The "500 on missing id" and
> "no `@ControllerAdvice` in this module" statements below (HTTP API table and Gotcha
> #10/#12) are now stale for this specific path; other unhandled exceptions in the
> module still fall through to Spring's default error body, since the handler only
> covers `NoSuchElementException` so far.

## Purpose

`order-service` owns carts, orders, order items, and discounts for the e-commerce
platform. It exposes a REST API for order CRUD and lookup (`OrderController`) and a cart
API (`CartController`): list/add/remove were added for GH #13; quantity-update
(`PUT .../items/{itemId}`) and clear-cart (`DELETE .../user/{userId}`, called by
frontend-service right after a successful checkout) were added for GH #6, closing that
seam. Checkout itself (cart -> order) is still orchestrated from frontend-service, not
here — this module only exposes the plain order-create endpoint that the checkout
handler calls. Its main orchestration responsibility is creating
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
│   ├── controller/OrderController.java   REST endpoints for orders
│   ├── controller/CartController.java    REST endpoints for cart list/add/remove (GH #13) + update/clear (GH #6)
│   ├── dto/                              CartDto, CartItemDto, CartItemUpdateRequest, OrderDto, OrderItemDto, DiscountDto
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
├── exception/GlobalExceptionHandler.java @RestControllerAdvice (GH #42) — maps common.exceptions.NoSuchElementException to 404/ErrorResponse; nothing else yet
└── infrastructure/
    ├── cache/CacheConfig.java            RedisCacheManager, RedisTemplate, StringRedisTemplate beans (unused — no @Cacheable anywhere)
    ├── config/FeignClientConfig.java     Feign logger level + productServiceErrorDecoder bean
    └── http/
        ├── client/                       ProductServiceClient, PaymentClient, ProductServiceErrorDecoder
        └── dto/                          ProductDto, InventoryDto, PaymentDto, CategoryDto (Feign response shapes)
```

No `dataAccess/dao/` — repositories are plain Spring Data interfaces. Package casing
follows the repo convention (`dataAccess`, not `dataaccess`/`infastructure`).

## Domain model

| Domain (POJO) | Entity | Notable fields | Notes |
|---|---|---|---|
| `Cart` | `CartEntity` (`@Table("cart")`) | `id, userId, createdBy, updatedBy, sessionId, status, cartItems, subtotal, discountTotal, taxTotal, shippingTotal, totalPrice, createdAt, updatedAt` | Domain model has `createdBy`/`updatedBy`; entity has them too. `@Component`-annotated domain POJO (see Gotchas). |
| `CartItem` | `CartItemEntity` (`@Table("cart_item")`) | `id, cartId, productId, productVariantId, storeId, productSku, productName, quantity, unitPrice, lineTotal, currency, createdAt, updatedAt` | Entity `cart` FK is `optional=false`/`nullable=false`; domain model only carries `cartId` (a UUID), not the parent reference. |
| `Order` | `OrderEntity` (`@Table("\"order\"")`, quoted because `order` is a SQL keyword) | `id, storeId, seller, buyer, shippingAddressId, status, selectedItems, discountsApplied, createdAt, updatedAt, createdBy, updatedBy` | `selectedItems`/`discountsApplied` setters are hand-written (Lombok `@Setter(AccessLevel.NONE)` + custom methods) to defensively copy into new `ArrayList`s. `shippingAddressId` (GH #58) is a plain nullable `UUID` column (`shipping_address_id`) referencing an address owned by `user-service` — order-service does **not** validate that the id exists or belongs to the buyer; that check happens entirely in `frontend-service`'s `CartController` before the create call is made (see frontend-service ai_doc). Wired through `dataAccess/mapper/OrderMapper` (entity <-> domain) and `application/mapper/OrderHttpMapper` (domain <-> `OrderDto`), same pattern as every other scalar field on `Order`. Not `@NonNull` on `OrderDto` (unlike `buyer`/`seller`/`storeId`), so existing/other callers that don't supply one (e.g. the dead `createOrderFromCart`/`convertCartToOrder` path) are unaffected. |
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
but nothing in this module ever transitions an order into them via `create` — they can
only be reached today through `PUT /api/v1/orders/{id}` (see HTTP API), which is now
guarded (GH #43): `OrderServiceImpl.update` enforces a legal transition graph -
`PENDING -> CONFIRMED|CANCELLED`, `CONFIRMED -> SHIPPED|CANCELLED`, `SHIPPED ->
DELIVERED`; `DELIVERED`/`CANCELLED` are terminal. A same-status update is a no-op. An
illegal transition throws `InvalidOrderStateException`
(`domain/exception/InvalidOrderStateException.java`), mapped locally in
`OrderController.updateOrder` to `409 Conflict` (no `GlobalExceptionHandler` exists in
this module - see HTTP API / Gotchas). The check is skipped if `id` doesn't correspond
to an existing order yet - see Gotcha 12, still unfixed.

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
  - `CartItemRepository`: `findByCartId`, `findByIdAndCartId`, `deleteByCartId`. (Two JPQL
    finder methods, `findByCartIdAndStoreId` / `findByCartIdAndProductId`, were never called
    anywhere in the module — removed as dead queries, issue #51.)
  - `OrderRepository`: `findByBuyer`, `findBySeller`, `findByStoreId`, `findByStatus`, plus
    JPQL `findByBuyerAndStoreId` / `findBySellerAndStoreId` / `findByBuyerAndStatus`.

## HTTP API

Base path: `ApiPaths.ORDER_BASE = /api/v1/orders`, defined in
`application/controller/OrderController.java`.

`CartController` (`ApiPaths.CART_BASE = /api/v1/carts`):

| Method | Path | Request body | Response | Status codes | Notes |
|---|---|---|---|---|---|
| GET | `/api/v1/carts/user/{userId}` | — | `CartDto` | 200 | Get-or-create: creates an empty `ACTIVE` cart for the user if none exists yet, via `CartService.getOrCreateActiveCart`. Added for GH #13. |
| GET | `/api/v1/carts/{id}` | — | `CartDto` | 200, 404 if not found | Direct cart lookup by id, mirrors `OrderController`'s `/{id}`. Added for GH #13. |
| POST | `/api/v1/carts/user/{userId}/items` | `CartItemDto` | `CartDto` (whole cart, updated) | 201 | Get-or-creates the user's active cart, then `CartService.addItem`. Added for GH #13. |
| DELETE | `/api/v1/carts/user/{userId}/items/{itemId}` | — | `CartDto` | 200, 404 if the cart is gone | Get-or-creates the user's active cart, then `CartService.removeItem`. Added for GH #13. |
| PUT | `/api/v1/carts/user/{userId}/items/{itemId}` | `CartItemUpdateRequest` (`{quantity}`) | `CartDto` | 200, 400 if `quantity < 1`, 404 if the item isn't in the user's active cart | Added for GH #6. Recomputes `lineTotal` from the item's existing `unitPrice` before calling `CartService.updateItem` (which only overwrites `quantity`/`lineTotal` verbatim), then calls `CartService.recalculateTotals` so the response cart total is in sync. |
| DELETE | `/api/v1/carts/user/{userId}` | — | `CartDto` (now empty) | 200 | Added for GH #6. Calls `CartService.clearCart` — empties items and zeroes totals, but the cart stays `ACTIVE` (no code path in this module ever transitions a cart to `CONVERTED`). Called by frontend-service's checkout handler right after a successful order create, so the same cart can't be checked out twice. |

The calling user is identified purely by the `{userId}` path variable, exactly like
`OrderController`'s `{buyerId}`/`{sellerId}` — this module does not read any
gateway-propagated identity header itself (see Security). `CartService.recalculateTotals`
is now wired (see above); it only recomputes `subtotal` from cart item `lineTotal`s, not
`discountTotal`/`taxTotal`/`shippingTotal`/`totalPrice` — those remain whatever they were
(a pre-existing partial implementation, not extended here).

| Method | Path | Request body | Response | Status codes | Auth |
|---|---|---|---|---|---|
| POST | `/api/v1/orders` | `OrderDto` | `OrderDto` | 201 Created, 400 on `@Valid` failure (GH #40 - see below), or 500 on any other unhandled exception — no `@ControllerAdvice` in this module | None enforced in-module; relies on upstream gateway/JWT filter |
| GET | `/api/v1/orders` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/{id}` | — | `OrderDto` or empty body | 200, 404 if not found | none |
| GET | `/api/v1/orders/buyer/{buyerId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/seller/{sellerId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/status/{status}` | — (path enum `OrderStatus`) | `List<OrderDto>` | 200, 400 if `status` doesn't parse to the enum | none |
| GET | `/api/v1/orders/buyer/{buyerId}/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/seller/{sellerId}/store/{storeId}` | — | `List<OrderDto>` | 200 | none |
| GET | `/api/v1/orders/buyer/{buyerId}/status/{status}` | — | `List<OrderDto>` | 200 | none |
| PUT | `/api/v1/orders/{id}` | `OrderDto` | `OrderDto` | 200, 400 on `@Valid` failure, 404 if `id` doesn't exist (GH #42: `update()` looks the order up first), 409 if the requested `status` is not a legal transition from the order's current status (GH #43, checked against that same lookup) | none |
| DELETE | `/api/v1/orders/{id}` | — | empty | 204, 404 if `id` doesn't exist (GH #42: `delete()` checks `existsById()` first) | none |

`OrderDto`/`OrderEntity` carry a `shippingAddressId` field (GH #58, see Domain model
above) — `POST /api/v1/orders` accepts it as an ordinary nullable field on the request
body; there is no dedicated shipping-address endpoint or path, and no ownership/existence
check against `user-service` happens in this module.

`createOrder`/`updateOrder` now both validate with `@Valid` (GH #40):
`OrderDto.buyer`/`seller`/`storeId` are `@NotNull`, `selectedItems` is `@NotEmpty` and
cascades (`@Valid`) into each `OrderItemDto`, whose `quantity`/`unitPrice` are
`@Positive`. `id`/`createdAt`/`updatedAt` are intentionally left without Bean Validation
constraints - they're server-managed (`id` is `GenerationType.UUID`, `createdAt`/
`updatedAt` are `@CreationTimestamp`/`@UpdateTimestamp`) and a normal create request
omits them; Lombok's `@NonNull` still guards them at the Java-construction level (e.g.
`OrderHttpMapper`/`OrderDto.builder()` call sites) but does not affect JSON
deserialization. There is still no `GlobalExceptionHandler` in this module, so a
`MethodArgumentNotValidException` from a failed `@Valid` falls through to Spring's
default 400 body, not `common.dto.ErrorResponse`.

`updateOrder` no longer lets the caller set `status` to an arbitrary `OrderStatus` value
— see the transition-guard note under `OrderStatus` above (GH #43).

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
3. Compares `inventory.getQuantity()` against `item.getQuantity()`; throws
   `InsufficientStockException` if insufficient. (Previously compared
   `inventory.getAvailableQuantity()` = `quantity - reservedQuantity`, but product-service's
   `reservedQuantity` was never written by anything — see product-service ai_doc GH #29 —
   so `getAvailableQuantity()` always equaled `quantity` anyway; the field and method were
   removed from both sides as dead weight, `infrastructure/http/dto/InventoryDto.java` now
   only carries `quantity`.)
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

### `createOrderFromCart` (dead/unreachable public method — investigated and rejected for GH #6)

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

**GH #6 confirmed this is still true and did not use it.** Checkout (frontend-service's
`CartController.placeOrder`) builds a fully-populated `OrderDto` from the cart's items
itself and calls the plain `POST /api/v1/orders` (`OrderController.createOrder` ->
`OrderService.create`) instead — the only order-creation path that is actually reachable
over HTTP. `createOrderFromCart` was left exactly as-is (not fixed, not wired to the
interface, not deleted) — fixing it was out of scope for GH #6, which only needed *a*
working create path, and the plain one already existed and worked.

## Outbound dependencies (Feign clients)

`@EnableFeignClients(basePackages = "com.kawashreh.ecommerce.order_service")` in
`OrderServiceApplication`.

| Client | `@FeignClient` name | Target base path | Used by | Notes |
|---|---|---|---|---|
| `ProductServiceClient` | `product-service` | `/api/v1/product`, `/api/v1/inventory` | `OrderServiceImpl` (`retrieveProduct`, `retrieveInventory`, `deductInventory`) | `checkInventoryAvailability` and `restoreInventory` are declared but **never called** anywhere in `src/main`. |
| `PaymentClient` | `payment-service` | `/api/v1/payment` | `OrderServiceImpl.create` (`processPayment`), issue #9 | ~~Was fully dead~~ — now wired: `create()` calls `paymentClient.processPayment` after confirming inventory. A prior version of this doc and issue #51 both called it dead; that's stale as of issue #9. |

`UserServiceClient` (`user-service`, `/api/v1/user/{userId}`) used to be declared here too —
genuinely dead, never injected or called anywhere in `src/main`, unlike `PaymentClient`.
Removed along with its Feign client config block and its DTO (issue #51).

- **Target URL resolution**: `application.yml` (base profile) points both remaining Feign
  client names at `${GATEWAY_URL:http://api-gateway:8765}` — i.e. order-service calls
  through the API gateway by default, per the root `CLAUDE.md` convention. The k8s
  `order-configmap.yaml` overrides this per-service to `http://product-service`,
  `http://payment-service` (direct in-cluster DNS, bypassing the gateway) — a real
  behavioral difference between the Docker Compose/local profile and the Kubernetes
  deployment, not just a URL substitution. (The configmap still has a leftover
  `user-service` entry, but per issue #52 these embedded ConfigMap `application.yml`
  blocks aren't actually mounted by any Deployment, so it's inert either way.)
- **Failure handling**:
  - `spring.cloud.openfeign.circuitbreaker.enabled: true` (`application.yml`) routes Feign
    calls through Resilience4j. This setting previously lived under a top-level `feign:`
    key, which Spring Cloud OpenFeign stopped reading as of 4.0 (see issue #57) — it bound
    to nothing and every Feign-level circuit-breaker/timeout/error-decoder setting was
    silently dead until it was moved to the `spring.cloud.openfeign.*` prefix it now uses.
  - `resilience4j.circuitbreaker.instances.product-service`: `slidingWindowSize: 10`,
    `minimumNumberOfCalls: 5`, `permittedNumberOfCallsInHalfOpenState: 3`,
    `waitDurationInOpenState: 5s`, `failureRateThreshold: 50`. No circuit breaker instance
    is configured for `payment-service` or `user-service`.
  - `resilience4j.retry.instances.product-service`: `maxAttempts: 3`, `waitDuration: 1s`.
    No retry instance configured for the other two clients.
  - No `@CircuitBreaker`/`@Retry` annotations appear anywhere in `src/main` — the
    Resilience4j config applies automatically at the Feign-client level because
    `spring.cloud.openfeign.circuitbreaker.enabled=true` wraps every Feign client method,
    keyed by the `@FeignClient` name (`product-service`). There is no fallback method/class
    configured, so an open circuit or exhausted retries on `product-service` simply
    propagates the underlying (or a `CallNotPermittedException`) exception up through
    `validateInventoryAvailability`'s catch-all, rewrapped as `IllegalArgumentException`.
    Verified live: with Docker running, `OrderServiceIntegrationTest` boots a real Spring
    context with this config bound and passes — confirming the property now actually binds,
    though no test forces a downstream failure to observe the breaker open in practice.
  - `spring.cloud.openfeign.client.config.default`: `connectTimeout: 5000`,
    `readTimeout: 10000`, `loggerLevel: basic`.
    `spring.cloud.openfeign.client.config.product-service.errorDecoder:
    com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceErrorDecoder`
    wires `ProductServiceErrorDecoder` only for the `product-service` client. This must be
    the fully-qualified class name, not a Spring bean name — `FeignClientFactoryBean`
    resolves this property via `Class<ErrorDecoder>` binding (`getBean(class)` falling back
    to `BeanUtils.instantiateClass(class)`), so a bean name here silently fails to bind and
    previously crashed the application context at startup once the prefix bug (#57) was
    fixed without also correcting this value.
  - `ProductServiceErrorDecoder` maps HTTP 404/400/503 (and a default case for everything
    else) to `ProductServiceException` with a status code and message. It does **not**
    apply to `PaymentClient` or `UserServiceClient`, which are not configured with any error
    decoder — they'd fall back to Feign's `ErrorDecoder.Default`.
  - `extractProductIdFromMethodKey` in `ProductServiceErrorDecoder` always returns the
    literal string `"unknown"` — it does not actually parse the method key despite the
    comment claiming it's "for logging purposes"; the resulting `ProductServiceException`
    always carries `productId = "unknown"`.
  - `FeignClientConfig` (`infrastructure/config/FeignClientConfig.java`) registers only
    `feignLoggerLevel()` (BASIC). It previously also declared a `productServiceErrorDecoder`
    `@Bean`, but that bean was never referenced via `@FeignClient(configuration = ...)` or
    `@EnableFeignClients(defaultConfiguration = ...)` on any client, and once the YAML
    property held the decoder's fully-qualified class name directly, the bean was dead code
    and was removed.

## Configuration

| Property | Default / value | Source | Notes |
|---|---|---|---|
| `spring.application.name` | `order-service` | `application.yml` | |
| `server.port` | `8080` | `application.yml` | `application-ide.yml` overrides to `8083`. Dockerfile comment claims "Port is dynamically assigned (server.port=0 in application.properties)" — that is only true for `src/test/resources/application-test.yml` (`server.port: 0`), **not** for the shipped `application.yml`; the Dockerfile comment is stale/incorrect for the running image. |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/orderdb` (base) | `application.yml`, overridden per profile (`-local`: `localhost:5433`, `-ide`: `localhost:5435`, k8s configmap: `postgres-server:5432`, test: `localhost:5432` via Testcontainers-injected URL) | |
| `spring.datasource.username` / `password` | `postgres` / *(none — required)* | `application.yml` | `password: ${SPRING_DATASOURCE_PASSWORD}` has no default — a missing env var fails startup rather than falling back to a committed value. Dev-only credential per root `CLAUDE.md` warning. |
| `spring.jpa.hibernate.ddl-auto` | `update` (main), `create-drop` (test) | `application.yml`, `application-test.yml` | |
| `spring.jpa.show-sql` | `true` | `application.yml`, `application-test.yml` | |
| `spring.data.redis.host` / `port` | `localhost:6379` (base default), `redis:6379` (`-local` profile — see Gotchas), `redis-server:6379` (k8s) | `application.yml`, `application-local.yml`, configmap | |
| `GATEWAY_URL` | `http://api-gateway:8765` (base default) | `application.yml`; `application-ide.yml` sets literal `http://localhost:8765` | |
| `spring.cloud.openfeign.circuitbreaker.enabled` | `true` | `application.yml` | Was a dead top-level `feign.circuitbreaker.enabled` until #57. |
| `spring.cloud.openfeign.client.config.default.connectTimeout` / `readTimeout` | `5000` / `10000` (ms) | `application.yml` | k8s configmap's embedded `application.yml` overrides `readTimeout` to `5000` for its own `default` client config block. |
| `spring.cloud.openfeign.client.config.product-service.errorDecoder` | `com.kawashreh.ecommerce.order_service.infrastructure.http.client.ProductServiceErrorDecoder` | `application.yml` | Must be a fully-qualified class name, not a bean name — see Outbound dependencies. |
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

- `src/test/java/.../OrderServiceIntegrationTest.java` — requires Docker.
  `@SpringBootTest` + `@Testcontainers` + `@ActiveProfiles("test")`, backed by a
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
  - **Not covered**: `CartService`/`CartServiceImpl` itself (zero test coverage for cart
    create, add/remove/update item, clear, recalc totals, or any `CartStatus` transition —
    `CartControllerTest`, below, mocks `CartService` so it doesn't exercise the impl);
    `OrderController` (no `@WebMvcTest`/`MockMvc` test exists — no endpoint, path
    variable, or status-code coverage); the compensating-cancel path (no test forces
    `deductInventory` to fail/return `false` to observe the `CANCELLED` transition or the
    transaction-rollback behavior documented above); Resilience4j circuit-breaker/retry
    behavior;
    `ProductServiceErrorDecoder`; caching (none exists to test).
- `src/test/java/.../domain/service/impl/OrderServiceImplTest.java` — plain
  Mockito unit test (no Docker needed) covering `OrderServiceImpl.create`'s
  success/failure branches directly (repository and `ProductServiceClient` mocked).
  GH #58 added two cases verifying `shippingAddressId` survives the domain -> entity ->
  repository -> domain round trip performed by `create()` (one with a real UUID, one
  confirming a `null` value is still accepted — required for backward compatibility with
  callers, such as the dead `createOrderFromCart`, that don't set it). These are unit
  tests against a mocked repository; they do not exercise a real database column/schema
  (that would require the Testcontainers-backed integration test, not run here — see
  below).
- `src/test/java/.../application/controller/CartControllerTest.java` — `@WebMvcTest(CartController.class)`
  slice test (no Docker needed), `CartService` mocked via `@MockitoBean`. Covers
  get-or-create by user, get-by-id (found/404), add-item (201), remove-item (200/404) —
  added for GH #13 — plus update-item (200 with recalculated totals, 404 when the item
  isn't in the cart, 400 for non-positive quantity) and clear-cart (200, empty result) —
  added for GH #6.
- Run: `mvn -pl order-service test` (the integration test requires a running Docker
  daemon for Testcontainers, per root `CLAUDE.md`; `OrderServiceImplTest` and
  `CartControllerTest` do not).

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
3. **(Fixed for GH #13, extended for GH #6)** `CartController` exposes list/add/remove
   (`GET /api/v1/carts/user/{userId}`, `GET /api/v1/carts/{id}`,
   `POST /api/v1/carts/user/{userId}/items`, `DELETE /api/v1/carts/user/{userId}/items/{itemId}`,
   all GH #13) plus quantity-update and clear-cart
   (`PUT /api/v1/carts/user/{userId}/items/{itemId}`, `DELETE /api/v1/carts/user/{userId}`,
   both GH #6). `CartService.recalculateTotals` is now called from the update-item
   endpoint; cart-to-order checkout itself is still orchestrated by frontend-service, not
   this module (see `createOrderFromCart`, below).
4. **(Fixed for GH #13) `CartMapper.toEntity` now sets the `CartItemEntity.cart`
   back-reference.** It previously mapped `d.getCartItems()` straight into the builder via
   `CartItemMapper.toEntityList`, which only maps scalar fields — no code ever called
   `item.setCart(...)`. Since `CartItemEntity.cart` is `nullable=false, optional=false`,
   persisting a `Cart` with any items (via `create()` or `update()`) would have thrown a
   constraint violation the first time the cart was actually used end-to-end. The fix
   builds the `CartEntity` first, then sets the back-reference on every mapped
   `CartItemEntity` before attaching the list, mirroring how `OrderServiceImpl.create`
   manually does `item.setOrder(entity)`.
   `order-service/src/main/java/.../dataAccess/mapper/CartMapper.java:10-33`.
5. ~~`PaymentClient` and `UserServiceClient` are fully dead code~~ — stale/partially
   fixed. `PaymentClient` is wired into `OrderServiceImpl.create` as of issue #9 (see
   Outbound dependencies). `UserServiceClient` really was fully dead — declared,
   configured in `application.yml`'s Feign client config block, never injected or called
   by any class in `src/main` — and was removed along with that config block and its DTO
   (issue #51).
6. **`ProductServiceClient.checkInventoryAvailability` is declared and never called** —
   `validateInventoryAvailability` re-implements the same check manually via
   `retrieveInventory` instead of calling this endpoint.
   `infrastructure/http/client/ProductServiceClient.java:23-26`.
7. **`createOrderFromCart` is dead and broken** — not on the `OrderService` interface (so
   unreachable through normal DI), and its helper `convertCartToOrder` never reads the
   cart's items (`selectedItems` stays empty), so calling it would always immediately fail
   `validateInventoryAvailability`'s empty-list check. `CartRepository`/`CartItemRepository`
   are not injected into `OrderServiceImpl` at all. `OrderServiceImpl.java:220-263`.
   Investigated for GH #6 and deliberately not used — see "createOrderFromCart" above.
8. **Redundant duplicate Feign calls to `retrieveProduct`** — called once in
   `validateInventoryAvailability` and again in `updateProductInventory` for the same item,
   purely to log the product id a second time. `OrderServiceImpl.java:69, 117`.
9. **Duplicate log line** — the two `logger.info("Inventory validation passed...")` calls
   at `OrderServiceImpl.java:92-95` log functionally the same message twice per item (once
   keyed by SKU, once by product id).
10. **No `@ControllerAdvice`/`GlobalExceptionHandler` for most exception types** — as of
    GH #42 the module has `exception/GlobalExceptionHandler`, but it only handles
    `common.exceptions.NoSuchElementException` (-> 404). Unhandled
    `IllegalArgumentException`, `InsufficientStockException`, and the wrapped
    `RuntimeException` from `create()` still surface as Spring Boot's default error
    response, not `common.dto.ErrorResponse`.
11. ~~**No request-body validation**~~ — fixed (GH #40): `OrderController.createOrder`/
    `updateOrder` now validate with `@Valid`; see HTTP API above for what's constrained.
12. ~~**`updateOrder`/`deleteOrder` have no not-found guard**~~ — fixed in GH #42:
    `update()`/`delete()` now look the order up first (`update()` via `findById()`, reused
    by GH #43's status-transition check; `delete()` via `existsById()`) and throw
    `common.exceptions.NoSuchElementException`, mapped to 404 by the new
    `GlobalExceptionHandler`.
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
