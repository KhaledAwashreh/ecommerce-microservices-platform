# frontend-service

## Purpose

Server-side-rendered web client for the platform, built on Spring MVC + Thymeleaf with
HTMX for partial-page interactivity (`frontend-service/pom.xml` pulls in
`spring-boot-starter-thymeleaf`, `spring-boot-starter-web`, and htmx is loaded client-side
via CDN `<script src="https://unpkg.com/htmx.org@1.9.10">` in every template). **This is
not a React app** — the root `README.md`'s description is stale on this point; there is no
`package.json`, no JS build, no SPA framework anywhere under `frontend-service/`. All
rendering happens server-side in Thymeleaf templates under
`frontend-service/src/main/resources/templates/`, with HTMX used for partial swaps
(address grid/modal, live product search-as-you-type). The service talks to every backend
service exclusively through the API gateway (`api.gateway.base-url`, default
`http://localhost:8765`), never directly. Despite the module's own Javadoc comments
claiming "Feign client... Uses Kubernetes DNS for service discovery" (see Gotchas), all
outbound calls are Spring Cloud OpenFeign clients pointed at gateway URLs — **not**
`WebClient`, contrary to what `WebClientConfig` and the root `CLAUDE.md` suggest (see
Gotchas).

## Package layout

```
frontend-service/src/main/java/com/kawashreh/ecommerce/frontend/
├── FrontendApplication.java        # @SpringBootApplication, @EnableFeignClients(basePackages=".. .client")
├── client/                         # @FeignClient interfaces, one per upstream domain, all routed through the gateway
│   ├── AddressServiceClient.java   # -> ${gateway}/api/v1/address (served by user-service)
│   ├── CartServiceClient.java      # -> ${gateway}/api/v1/carts (order-service) — added for GH #13
│   ├── CategoryServiceClient.java  # -> ${gateway}/api/v1/categories (product-service)
│   ├── InventoryServiceClient.java # -> ${gateway}/api/v1/inventory (product-service)
│   ├── OrderServiceClient.java     # -> ${gateway}/api/v1/orders (order-service)
│   ├── PaymentServiceClient.java   # -> ${gateway}/api/v1/payment (payment-service)
│   ├── ProductServiceClient.java   # -> ${gateway}/api/v1/product (product-service)
│   ├── RoleServiceClient.java      # -> ${gateway}/api/v1/roles (user-service) — UNUSED, dead code
│   └── UserServiceClient.java      # -> ${gateway}/api/v1/user (user-service)
├── config/
│   ├── BearerTokenInterceptor.java # feign.RequestInterceptor — attaches session JWT as "Authorization: Bearer <token>" to every Feign call
│   ├── SessionManager.java         # thin wrapper over HttpSession: store/get JWT + username, isAuthenticated, invalidate
│   └── WebClientConfig.java        # defines a WebClient @Bean — unused anywhere in the module (dead code, see Gotchas)
├── controller/                     # @Controller (not @RestController) classes returning Thymeleaf view names
│   ├── AuthController.java         # /, /login, /register, /logout
│   ├── CartController.java         # /cart, /cart/add, /cart/remove wired to CartServiceClient (GH #13); /checkout still static
│   ├── InventoryController.java    # /inventory, /inventory/{id}
│   ├── OrderController.java        # /orders, /orders/{id}, POST /orders
│   ├── ProductController.java      # /products, /products/{id}, /products/search
│   └── ProfileController.java      # /profile, /profile/edit, /addresses/**
├── dto/                             # HTTP-facing DTOs shared with upstream services (plain POJOs, Lombok @Data/@Builder)
│   ├── AddressDto, CartDto, CartItemDto, CategoryDto, InventoryDto, OrderDto, OrderItemDto,
│   │   PaymentRequestDto, PaymentResponseDto, ProductDto, ProductVariationDto, RoleDto, UserDto
│   ├── UserLoginDto, UserRegisterDto            # UNUSED — dead duplicates of dto/request/UserLoginRequest & UserRegisterRequest
│   ├── facade/                      # composite view-model DTOs built by the facade layer
│   │   ├── OrderWithDetailsDto      # {order: OrderDto, payment: PaymentResponseDto}
│   │   ├── ProductWithDetailsDto    # {product: ProductDto, category: CategoryDto}
│   │   └── ProfileWithAddressesDto  # {user: UserDto, addresses: List<AddressDto>}
│   └── request/                     # inbound form-bound / request DTOs
│       ├── AddressRequest, RoleRequest, UserLoginRequest, UserRegisterRequest, UserUpdateRequest
├── exception/
│   └── GlobalExceptionHandler.java  # @ControllerAdvice — maps FeignException/DuplicateEntityException/NoSuchElementException/IllegalArgumentException/Exception to redirect-with-error-query-param
└── facade/                          # composes 2 Feign clients each, swallows downstream errors
    ├── OrderFacade.java             # OrderServiceClient + PaymentServiceClient -> OrderWithDetailsDto
    ├── ProductFacade.java           # ProductServiceClient + CategoryServiceClient -> ProductWithDetailsDto
    └── ProfileFacade.java           # UserServiceClient + AddressServiceClient -> ProfileWithAddressesDto
```

Resources:
```
frontend-service/src/main/resources/
├── application.yml       # port 3000, thymeleaf cache=false, api.gateway.base-url, jwt.secret/expiration (unused, see Gotchas), zipkin, actuator
├── application-ide.yml   # IDE profile override: zipkin base-url localhost instead of docker hostname
└── templates/
    ├── layout/base.html          # fragments only: navbar, footer, toast — not a layout dialect base page (no thymeleaf-layout-dialect dependency)
    ├── auth/{login,register}.html
    ├── product/{list,detail}.html
    ├── cart/{cart,checkout}.html
    ├── inventory/inventory.html  # inventory/detail.html is referenced by the controller but does NOT exist (see Gotchas)
    ├── order/orders.html         # order/detail.html is referenced by the controller but does NOT exist (see Gotchas)
    └── user/{profile,edit,addresses,address-grid,address-modal}.html
```

No `domain/`, `dataAccess/`, `constants/` packages exist in this module — it deviates from
the four-layer backend convention documented in the root `CLAUDE.md` (expected for a
presentation-only module with no persistence).

## Domain model

None. This module has no domain entities — it is a pure presentation/composition layer.
The closest thing to a domain model is the `dto` package (data shapes mirrored from
upstream services) and the `dto/facade` package (view-models composed from 2+ upstream
DTOs for a single page).

## Persistence

**None in practice**, despite what the build and test scaffolding imply:

- `pom.xml` has **no** `spring-boot-starter-data-jpa` and **no** `@Entity`/`@Repository`
  classes exist anywhere under `src/main/java`.
- `pom.xml` does declare `org.postgresql:postgresql` (runtime driver) and
  `spring-boot-starter-test` + `testcontainers` (`junit-jupiter`, `postgresql`) as test
  dependencies.
- `src/test/java/.../BaseIntegrationTest.java` spins up a `PostgreSQLContainer`, sets
  `spring.jpa.hibernate.ddl-auto=create-drop` via `@DynamicPropertySource`, and
  `src/test/resources/application-test.yml` configures a `frontenddb` datasource — but
  since there is no JPA starter and no entities, none of this JPA config does anything.
  This is dead test scaffolding (see Tests and Gotchas).

## HTTP API — UI routes

All routes below are served by `@Controller` classes returning Thymeleaf view names
(or `"redirect:..."` strings). "Auth required" means the handler calls
`sessionManager.isAuthenticated(request)` and redirects to `/login` if false — there is
no Spring Security filter chain; auth is enforced ad hoc, per handler.

| Method | Path | Controller | Template returned | What it does | Auth required |
|---|---|---|---|---|---|
| GET | `/` | AuthController | `redirect:/products` | Home redirect | No |
| GET | `/login` | AuthController | `auth/login` | Login form, shows `error` query param if present | No |
| POST | `/login` | AuthController | `redirect:/products` | Calls `UserServiceClient.login`, stores returned token string in session via `SessionManager.storeToken` | No |
| GET | `/register` | AuthController | `auth/register` | Registration form | No |
| POST | `/register` | AuthController | `redirect:/login?registered=true` (or `redirect:/register?error=...` if birthdate missing) | Calls `UserServiceClient.register` | No |
| POST | `/logout` | AuthController | `redirect:/login` | `SessionManager.invalidate` | No |
| GET | `/products` | ProductController | `product/list` | Lists all products via `ProductFacade.getAllProducts()`; if unauthenticated, renders with an empty list (page itself loads for anonymous users) | No (page loads either way, but only shows products if authenticated — see Gotchas) |
| GET | `/products/{productId}` | ProductController | `product/detail` | `ProductFacade.getProductWithDetails` | No |
| GET | `/products/search` | ProductController | `product/list` | Full-page (not fragment) search results via `ProductFacade.searchProducts` (in-memory substring filter over `getAllProducts()`, not a backend search call) | No |
| GET | `/cart` | CartController | `cart/cart` | `CartServiceClient.getCartForUser(userId)` (get-or-create); userId resolved via `ProfileFacade.getUserByUsername(session username)`, same pattern as `OrderController` | Yes |
| POST | `/cart/add` | CartController | `redirect:/cart` | Looks up the product via `ProductFacade.getProductWithDetails`, builds a `CartItemDto`, calls `CartServiceClient.addItem(userId, item)` | Yes |
| POST | `/cart/remove` | CartController | `redirect:/cart` | `CartServiceClient.removeItem(userId, itemId)` | Yes |
| GET | `/checkout` | CartController | `cart/checkout` | Static checkout form (hardcoded sample line item, hardcoded totals) — unchanged, still not wired to the cart (see Gotchas) | Yes |
| GET | `/inventory` | InventoryController | `inventory/inventory` | Always renders empty inventory list — `// TODO: Implement actual inventory list - currently stubbed` | Yes |
| GET | `/inventory/{productVariationId}` | InventoryController | `inventory/detail` | Calls `InventoryServiceClient.getInventoryByVariation`. **`inventory/detail.html` does not exist** — see Gotchas | Yes |
| GET | `/orders` | OrderController | `order/orders` | `OrderFacade.getOrdersWithPayments(buyerId)`, buyer resolved via `ProfileFacade.getUserByUsername(session username)` | Yes |
| GET | `/orders/{orderId}` | OrderController | `order/detail` | `OrderFacade.getOrderWithPayment`. **`order/detail.html` does not exist** — see Gotchas | Yes |
| POST | `/orders` | OrderController | `redirect:/orders/{id}` or `redirect:/checkout` | Consumes `@RequestBody OrderDto` (JSON). **No template in this module submits to this endpoint** — see Gotchas | Yes |
| GET | `/profile` | ProfileController | `user/profile` | `ProfileFacade.getProfileWithAddresses(username)` | Yes |
| GET | `/profile/edit` | ProfileController | `user/edit` | `ProfileFacade.getUserByUsername` | Yes |
| POST | `/profile/edit` | ProfileController | `redirect:/profile` | `UserServiceClient.updateUser` | Yes |
| GET | `/addresses` | ProfileController | `user/addresses` | `ProfileFacade.getAllAddresses()` | Yes |
| GET | `/addresses/grid` | ProfileController | `user/address-grid :: grid` (fragment) | HTMX partial, also used as the address-updated auto-refresh target per the grid's own `hx-trigger` | Yes (returns fragment even when unauthenticated — see Gotchas) |
| GET | `/addresses/modal` | ProfileController | `user/address-modal :: modal` (fragment) | HTMX partial for the add/edit address form; `id` query param toggles add vs edit | Yes |
| POST | `/addresses/add` (header `HX-Request=true`) | ProfileController | `user/address-grid :: grid` | `AddressServiceClient.createAddress`, returns refreshed grid fragment | Yes |
| POST | `/addresses/add` (no HTMX header) | ProfileController | `redirect:/addresses` | Non-JS fallback for the same action | Yes |
| POST | `/addresses/edit/{id}` (header `HX-Request=true`) | ProfileController | `user/address-grid :: grid` | `AddressServiceClient.updateAddress` | Yes |
| POST | `/addresses/delete` | ProfileController | `redirect:/addresses` | `AddressServiceClient.deleteAddress` | Yes |

Notes on gaps in the route table:
- There is **no non-HTMX fallback** for `POST /addresses/edit/{id}` (only the
  `HX-Request=true`-gated handler exists) — see Gotchas.
- There is **no handler at all** for `POST /checkout/place`, which is the actual
  `action` the checkout form (`cart/checkout.html`) submits to — see Gotchas.
- There is **no handler** for `POST /cart/update`, which `cart/cart.html`'s quantity
  `<select>` submits to on change — see Gotchas.
- There is **no "set default address" handler** — the button exists in
  `address-grid.html` with no `hx-*` or form action at all.

## Outbound dependencies — Feign clients

All clients are `@FeignClient` interfaces in `client/`, registered via
`@EnableFeignClients(basePackages = "com.kawashreh.ecommerce.frontend.client")` in
`FrontendApplication.java`. Every client's `url` is `${api.gateway.base-url}` plus a
fixed path — none use Eureka/service-discovery `name`-based resolution; the `name`
attribute is present only for Feign's internal bookkeeping (circuit breaker naming,
metrics). `BearerTokenInterceptor` (a `feign.RequestInterceptor`, applied to all Feign
clients globally) attaches `Authorization: Bearer <token>` by reading the token
`SessionManager` stored in the current `HttpSession`. Failure handling: each facade
method wraps its Feign calls in `try/catch (Exception e)`, returning `null` or an empty
collection on failure — no retry, no circuit breaker configuration is defined in this
module (Resilience4j lives in `api-gateway`, not here). `GlobalExceptionHandler` catches
any exception that a controller lets propagate (i.e. anything not caught by a facade
first) and redirects back to a caller-appropriate path with `?error=<message>`.

| Client method | Gateway path called | Owning service (per root `CLAUDE.md`) | Used by |
|---|---|---|---|
| `UserServiceClient.register` | `POST /api/v1/user/register` | user-service | `AuthController.register` |
| `UserServiceClient.login` | `POST /api/v1/user/login` | user-service | `AuthController.login` |
| `UserServiceClient.getUserById` | `GET /api/v1/user/{userId}` | user-service | `ProfileFacade.getUserById` (facade method itself is unused externally — see Gotchas) |
| `UserServiceClient.getUserByUsername` | `GET /api/v1/user?username=` | user-service | `ProfileFacade.getUserByUsername` / `getProfileWithAddresses` — used by OrderController, ProfileController |
| `UserServiceClient.updateUser` | `PUT /api/v1/user/{userId}` (header `X-User-ID`) | user-service | `ProfileController.updateProfile` |
| `AddressServiceClient.getAddresses` | `GET /api/v1/address` | user-service | `ProfileFacade.getAllAddresses` / `getProfileWithAddresses` |
| `AddressServiceClient.getAddressById` | `GET /api/v1/address/{id}` | user-service | `ProfileController.addressModal` |
| `AddressServiceClient.createAddress` | `POST /api/v1/address` (header `X-User-ID`) | user-service | `ProfileController.addAddressHtmx` / `addAddress` |
| `AddressServiceClient.updateAddress` | `PUT /api/v1/address/{id}` (header `X-User-ID`) | user-service | `ProfileController.editAddressHtmx` |
| `AddressServiceClient.deleteAddress` | `DELETE /api/v1/address/{id}` (header `X-User-ID`) | user-service | `ProfileController.deleteAddress` |
| `CartServiceClient.getCartForUser` | `GET /api/v1/carts/user/{userId}` | order-service | `CartController.cart` — added for GH #13 |
| `CartServiceClient.addItem` | `POST /api/v1/carts/user/{userId}/items` | order-service | `CartController.addToCart` — added for GH #13 |
| `CartServiceClient.removeItem` | `DELETE /api/v1/carts/user/{userId}/items/{itemId}` | order-service | `CartController.removeFromCart` — added for GH #13 |
| `RoleServiceClient.getAll/getById/create/delete` | `/api/v1/roles/**` | user-service | **Nobody** — unused, dead code |
| `ProductServiceClient.getAllProducts` | `GET /api/v1/product` | product-service | `ProductFacade.getAllProducts` / `searchProducts` |
| `ProductServiceClient.getProductById` | `GET /api/v1/product/{id}` | product-service | `ProductFacade.getProductWithDetails` |
| `CategoryServiceClient.getAllCategories` | `GET /api/v1/categories` | product-service | `ProductFacade.getAllCategories` (facade method itself is unused externally — no controller calls it) |
| `CategoryServiceClient.getCategoryById` / `getCategoryByName` | `GET /api/v1/categories/{id}` / `/name/{name}` | product-service | **Nobody** — unused |
| `InventoryServiceClient.getInventoryByVariation` | `GET /api/v1/inventory/product-variation/{id}` | product-service | `InventoryController.inventoryDetail` |
| `InventoryServiceClient.checkAvailability` | `GET /api/v1/inventory/product-variation/{id}/availability` | product-service | **Nobody** — unused |
| `InventoryServiceClient.deductStock` / `restoreStock` | `PUT /api/v1/inventory/product-variation/{id}/deduct`/`/restore` | product-service | **Nobody** — unused |
| `OrderServiceClient.createOrder` | `POST /api/v1/orders` | order-service | `OrderFacade.createOrder`, called from `OrderController.createOrder` — but no template posts to `/orders` (see Gotchas) |
| `OrderServiceClient.getAllOrders` | `GET /api/v1/orders` | order-service | **Nobody** — unused |
| `OrderServiceClient.getOrderById` | `GET /api/v1/orders/{id}` | order-service | `OrderFacade.getOrderWithPayment` |
| `OrderServiceClient.getOrdersByBuyer` | `GET /api/v1/orders/buyer/{buyerId}` | order-service | `OrderFacade.getOrdersByBuyer` / `getOrdersWithPayments` |
| `PaymentServiceClient.processPayment` | `POST /api/v1/payment/process` | payment-service | **Nobody** — unused (checkout never calls it) |
| `PaymentServiceClient.getPayment` | `GET /api/v1/payment/{id}` | payment-service | **Nobody** — unused |
| `PaymentServiceClient.getPaymentByOrderId` | `GET /api/v1/payment/order/{orderId}` | payment-service | `OrderFacade.getOrderWithPayment` |

Gateway routing was verified against `api-gateway/src/main/resources/application.yml`:
`/api/v1/user/**`, `/api/v1/roles/**`, and `/api/v1/address/**` route to user-service;
`/api/v1/product/**`, `/api/v1/categories/**`, and `/api/v1/inventory/**` route to
product-service; `/api/v1/orders/**` and `/api/v1/carts/**` (the latter added for GH #13,
route id `order-cart-service`) route to order-service; `/api/v1/payment/**` routes
to payment-service. Every frontend Feign client base path has a matching gateway route —
no routing gaps.

### DTO field parity vs upstream services

Fields below exist on the frontend DTO but have **no counterpart** on the corresponding
upstream service's DTO — they always deserialize as `null`/`false` from a real gateway
response:

| Frontend DTO | Extra field(s) with no upstream counterpart | Upstream DTO checked |
|---|---|---|
| `dto/OrderItemDto.java:19-21` | `productName`, `variationName`, `totalPrice` | `order-service/.../application/dto/OrderItemDto.java` (has only `id, productSku, quantity, unitPrice, createdAt, updatedAt, createdBy, updatedBy`) |
| `dto/OrderDto.java:40-43` | `subtotal`, `discountTotal`, `taxTotal`, `totalAmount` | `order-service/.../application/dto/OrderDto.java` (has no such fields; has `discountsApplied`, which frontend's `OrderDto` lacks entirely) |
| `dto/ProductDto.java:26-29` | `variations`, `minPrice`, `maxPrice`, `active` | `product-service/.../application/dto/ProductDto.java` (stops at `thumbnailUrl`) |
| `dto/PaymentRequestDto.java:19-22` | `cardNumber`, `cardHolderName`, `expiryDate`, `cvv` | `payment-service/.../application/dto/PaymentRequestDto.java` (has only `orderId, buyerId, amount, paymentMethod`) — moot in practice since `PaymentServiceClient.processPayment` has no callers (see Gotchas) |
| `dto/PaymentResponseDto.java:27` | `failureReason` | `payment-service/.../application/dto/PaymentResponseDto.java` |
| `dto/UserDto.java:22-23` | `createdAt`, `updatedAt` | upstream user-service response DTO has no such fields |

`OrderDto.getTotalPrice()` (`dto/OrderDto.java:49-56`) exists specifically to work around
the missing `totalAmount`/`subtotal` fields by deriving a total client-side from
`selectedItems` instead of trusting a field that would otherwise always be null.

Fields that do line up cleanly (checked, not listed above): `UserRegisterRequest` /
upstream registration DTO, `UserLoginRequest`, `UserUpdateRequest`, `AddressDto` /
upstream address response, `AddressRequest` / upstream create+update address requests,
`CategoryDto`, `InventoryDto`.

## Configuration

| Property | File | Default | Notes |
|---|---|---|---|
| `server.port` | `application.yml` | `3000` | |
| `spring.thymeleaf.cache` | `application.yml` | `false` | Hot-reload templates in dev |
| `spring.thymeleaf.prefix` / `.suffix` | `application.yml` | `classpath:/templates/` / `.html` | |
| `api.gateway.base-url` | `application.yml` | `${API_GATEWAY_BASE_URL:http://localhost:8765}` | Base URL for every Feign client |
| `jwt.secret` | `application.yml` | hardcoded hex string | **Unused** — no class in this module reads `jwt.*` or uses `io.jsonwebtoken` despite the `jjwt-api/impl/jackson` deps in `pom.xml` (see Gotchas) |
| `jwt.expiration` | `application.yml` | `1800000` | Same — unused |
| `management.zipkin.tracing.endpoint` | `application.yml` / `application-ide.yml` | `${ZIPKIN_BASE_URL:http://zipkin:9411}/api/v2/spans` (docker) vs `http://localhost:9411/api/v2/spans` (ide profile) | |
| `management.endpoints.web.exposure.include` | `application.yml` | `health,info,metrics,prometheus` | |
| `management.tracing.sampling.probability` | `application.yml` | `1.0` | |

`Dockerfile` builds a multi-stage image (`maven:3.9-eclipse-temurin-21` builder →
`eclipse-temurin:21-jre` runtime), exposes `8080` internally (note: this does not match
`server.port: 3000` in `application.yml` — see Gotchas), and defines a `curl`-based
`HEALTHCHECK` against `/actuator/health`.

## Caching

None. No `CacheConfig`, no `@Cacheable` annotations, no Redis dependency in this module.

## Security

- Auth is session-based on top of an opaque bearer token: `UserServiceClient.login`
  returns a raw `String` (presumed JWT, issued by `user-service`) which
  `SessionManager.storeToken` puts into the servlet `HttpSession` under key `jwt_token`,
  alongside `username`. There is no local JWT parsing/validation in this module — the
  frontend treats the token as opaque and just forwards it.
- `BearerTokenInterceptor` attaches `Authorization: Bearer <token>` to every outbound
  Feign request by reading `SessionManager.getToken(request)` off
  `RequestContextHolder`'s current request.
- "Auth required" is enforced per-controller-method via
  `sessionManager.isAuthenticated(request)` checks — there is no Spring Security filter
  chain, no `@PreAuthorize`, no centralized interceptor/filter enforcing this. Every
  handler that needs auth must remember to call it (and at least one does not — see
  Gotchas, product listing).
- Logout (`POST /logout`) calls `HttpSession.invalidate()` via `SessionManager.invalidate`.
  No explicit session timeout is configured (`server.servlet.session.timeout` is absent),
  so the Spring Boot default (30 minutes) applies.
- Passwords are never held by this module beyond the raw form field
  (`rawPassword` on `UserRegisterRequest`) — hashing happens in `user-service`.
- `jwt.secret` in `application.yml` is present but **unused** by any class in this module
  (see Configuration/Gotchas) — it is dead configuration, not a live signing key here.

## Tests

**There are effectively no tests in this module.** `src/test/java/.../BaseIntegrationTest.java`
is an abstract `@SpringBootTest` + `@Testcontainers` base class (spins up a
`PostgreSQLContainer`, wires `spring.datasource.*` and `spring.jpa.hibernate.ddl-auto`
dynamically) — but **no test class in the module extends it**, and no other test class
exists anywhere under `frontend-service/src/test`. There are zero `@Test` methods.
`src/test/resources/application-test.yml` (a `test` profile datasource + JPA config) is
likewise unreferenced by any actual test. Since the module has no JPA starter and no
entities at all (see Persistence), this scaffolding could not exercise anything even if a
test extended it. Running `mvn -pl frontend-service test` executes nothing.

## Gotchas

1. **Module is not WebClient-based, contradicting `WebClientConfig` and root `CLAUDE.md`.**
   `frontend-service/src/main/java/.../config/WebClientConfig.java` defines a `WebClient`
   `@Bean`, but no class anywhere in `src/main` injects or calls `WebClient` — every
   outbound call goes through `@FeignClient` interfaces in `client/`. The root
   `CLAUDE.md` states "`frontend-service` uses `WebClient` against the gateway base URL,
   forwarding the bearer token via `BearerTokenInterceptor`" — that description is
   inaccurate: `BearerTokenInterceptor` implements `feign.RequestInterceptor`, not a
   `WebClient` filter. `WebClientConfig.java` is dead code.
2. **`README.md` describes this module as a React app; it is server-rendered Thymeleaf + HTMX.**
   No `package.json`/JS build exists under `frontend-service`.
3. **`order/orders.html` iterates a model attribute typed as `List<OrderWithDetailsDto>` as if it were `List<OrderDto>`.**
   `OrderController.orders()` (`frontend-service/src/main/java/.../controller/OrderController.java:44-45`)
   puts `List<OrderWithDetailsDto>` into the model under key `"orders"`.
   `OrderWithDetailsDto` (`.../dto/facade/OrderWithDetailsDto.java`) only has fields
   `order` and `payment`. But `order/orders.html:24,28,32-36,40,45,50-53` accesses
   `order.createdAt`, `order.totalPrice`, `order.status`, `order.id`, `order.selectedItems`
   directly on the loop variable — none of which exist on `OrderWithDetailsDto`; they
   exist one level down, on `order.order.*`. This will throw a SpringEL
   property-not-found error for any non-empty orders list. Severity: **critical** — the
   `/orders` page is broken whenever a user has at least one order. Commit `edaa5b8`
   ("fix: add computed totalPrice to OrderDto...") added `OrderDto.getTotalPrice()` but
   did not fix this root-cause nesting mismatch, so the page remains broken.
4. **`inventory/detail.html` does not exist.** `InventoryController.inventoryDetail()`
   (`.../controller/InventoryController.java:45`) returns view name `"inventory/detail"`,
   but only `inventory/inventory.html` exists under
   `frontend-service/src/main/resources/templates/inventory/`. Any hit on
   `GET /inventory/{productVariationId}` throws `TemplateInputException`. Severity: **critical**.
5. **`order/detail.html` does not exist.** `OrderController.orderDetail()`
   (`.../controller/OrderController.java:61`) returns view name `"order/detail"`, but
   only `order/orders.html` exists under `templates/order/`. Any hit on
   `GET /orders/{orderId}` throws `TemplateInputException`. Severity: **critical**.
6. **Checkout form posts to a URL with no handler — still open, deliberate seam for GH #6.**
   `cart/checkout.html:23` (`<form action="/checkout/place" method="post">`) submits to
   `POST /checkout/place`, but no controller in this module maps that path
   (`CartController` only maps `GET /checkout`; `OrderController` maps `POST /orders`, a
   different path with a different, JSON `@RequestBody` contract). Clicking "Place Your
   Order" still 404s — GH #13 intentionally did not implement this (it depends on cart
   list/add/remove working first, which is now the case). Severity: **critical**.
7. **Cart quantity-change form posts to a URL with no handler — still open, deliberate
   seam for GH #6.** `cart/cart.html:50` (`<form action="/cart/update" method="post">`,
   auto-submitted via `onchange="this.form.submit()"`) targets `POST /cart/update`, which
   `CartController` still does not define (only `/cart/add` and `/cart/remove` were wired
   for GH #13). `CartService.updateItem` already exists in order-service and is reachable
   for a future handler to call. Severity: **high**.
8. **(Fixed for GH #13) Cart is now wired to a real backend.** `CartController` now
   injects `CartServiceClient`, `ProfileFacade`, and `ProductFacade`; `/cart` renders the
   caller's actual cart (get-or-create via `CartServiceClient.getCartForUser`),
   `POST /cart/add` resolves the product via `ProductFacade` and calls
   `CartServiceClient.addItem`, `POST /cart/remove` calls
   `CartServiceClient.removeItem`. `cart/cart.html` was also fixed to read `item.lineTotal`
   instead of a nonexistent `item.totalPrice`, and the `item.variationName` reference
   (no such data exists anywhere in the chain) was removed — either would have thrown a
   Thymeleaf property-not-found error the moment the cart held an item.
   **Known limitation, not fixed by GH #13**: neither the real `product-service`
   `ProductDto` nor any Feign client reachable from `frontend-service` returns a unit
   price or SKU at the product level (only per-variation data would, and there is no
   variation-selection UI on the product page) — `CartController.addToCart` currently
   sends `unitPrice`/`lineTotal` as `BigDecimal.ZERO` and a placeholder
   `productSku = product.getId().toString()`. `checkout.html` still displays hardcoded
   sample data. Severity: **medium** (cart mechanics work end-to-end; pricing data is a
   pre-existing gap one level up the stack).
9. **`OrderController.createOrder` (`POST /orders`) is unreachable from any template.**
   It consumes `@RequestBody OrderDto` (JSON body), but no template in this module POSTs
   JSON to `/orders` — the only form-based checkout path (`/checkout/place`) doesn't
   exist as a handler (see #6). This makes `OrderFacade.createOrder` /
   `OrderServiceClient.createOrder` effectively dead from the UI, reachable only via a
   raw JSON HTTP client. Severity: **medium**.
10. **`PaymentServiceClient.processPayment` is never called anywhere.** Nothing in
    `OrderFacade`, `OrderController`, or the checkout templates initiates a payment —
    checkout has no code path that calls the payment service to actually charge/process
    anything, despite `cart/checkout.html` presenting payment-method radio buttons.
    Severity: **medium**.
11. **No non-HTMX fallback exists for editing an address.** `ProfileController` defines
    `addAddressHtmx` (`HX-Request=true`) and a second, unconstrained `addAddress` for the
    add flow (`.../controller/ProfileController.java:116-146`), but only the
    HTMX-gated `editAddressHtmx` (`:150-166`) exists for edit — there is no
    `@PostMapping("/addresses/edit/{id}")` without the header constraint. If JS is
    disabled/HTMX fails to load, the edit form's `th:action` fallback
    (`user/address-modal.html:13`) has no handler to hit. Severity: **medium**.
12. **No "set default address" handler exists.** `user/address-grid.html:22` renders a
    "Set as Default" `<button>` with no `hx-*` attributes and no enclosing `<form>` — it
    does nothing when clicked. No controller method or Feign client method sets a
    default address either. Severity: **medium** (dead/non-functional UI element).
13. **Search bar targets a `#search-results` element that exists nowhere.**
    `layout/base.html:37` (`hx-get="/products/search" ... hx-target="#search-results"`)
    is rendered on every page via the `navbar` fragment, but no template in the module
    defines an element with `id="search-results"`. Additionally,
    `ProductController.searchProducts` (`.../controller/ProductController.java:52-62`)
    returns the **full** `product/list` page (with `<html>`/`<head>`/navbar/footer), not
    a fragment — so even if the target existed, HTMX would swap an entire document
    fragment into a `<div>`. Severity: **high** (primary search UI is non-functional).
14. **`address-modal.html` uses an invalid Thymeleaf attribute.**
    `user/address-modal.html:1` has `th:attrimpl:style="'display: flex;'"` — `attrimpl`
    is not a declared Thymeleaf dialect/namespace anywhere in this module, so this
    attribute is not processed and is emitted to the browser as a literal, non-standard
    HTML attribute (`th:attrimpl:style="display: flex;"`). It appears to have been
    intended as `th:style`. The modal's actual visibility is instead handled entirely by
    the inline `<script>` in `user/addresses.html:36-47` listening for
    `htmx:afterSwap`/`htmx:beforeSwap`, so this dead attribute happens not to break
    anything currently, but it is broken markup. Severity: **low**.
15. **`RoleServiceClient`, `RoleDto`, `RoleRequest` are entirely dead code.** No
    controller, facade, or template in this module references any of them — there is no
    role-management UI at all. Severity: **low**.
16. **`UserLoginDto` and `UserRegisterDto` (in `dto/`, not `dto/request/`) are dead duplicates.**
    They are never referenced by any client, controller, or facade — the module actually
    uses `dto/request/UserLoginRequest` and `dto/request/UserRegisterRequest` for the same
    purpose. Severity: **low**.
17. **`InventoryServiceClient.checkAvailability`/`deductStock`/`restoreStock` and
    `CategoryServiceClient.getCategoryById`/`getCategoryByName` and
    `OrderServiceClient.getAllOrders` and `PaymentServiceClient.getPayment` are all
    unused.** No caller anywhere in this module. Severity: **low** (dead client methods).
18. **`ProfileFacade` swallows exceptions via `System.out.print` instead of a logger.**
    `.../facade/ProfileFacade.java:33,39` — `System.out.print(e.getMessage())` — every
    other facade/controller in the module uses SLF4J (`OrderController`,
    `ProductController` both declare a `Logger`); `ProfileFacade` does not, and doesn't
    even use `println` (no trailing newline). Severity: **low**.
19. **`server.port` mismatch between `application.yml` (3000) and `Dockerfile`'s
    `EXPOSE`/`HEALTHCHECK` (8080).** `application.yml:1-2` sets `server.port: 3000`, but
    `Dockerfile:32-35` health-checks `http://localhost:8080/actuator/health` and
    `EXPOSE 8080`. Since `application.yml` is unconditionally active (no profile guard),
    the container's actual listening port is 3000, not 8080 — the Docker healthcheck
    would fail to connect (though root `CLAUDE.md` documents "Frontend —
    http://localhost:3000" for the compose setup, implying compose port-maps around this;
    the Dockerfile's own healthcheck is still targeting the wrong in-container port).
    Severity: **medium**.
20. **`jwt.secret`/`jwt.expiration` config and the `jjwt-api`/`jjwt-impl`/`jjwt-jackson`
    Maven dependencies are unused.** No class in `frontend-service/src/main` imports
    `io.jsonwebtoken` or reads `jwt.*` properties — the module treats the token from
    `user-service` as an opaque string. Dead config and dead dependencies. Severity: **low**.
21. **`UserServiceClient`'s Javadoc is stale/misleading.**
    `.../client/UserServiceClient.java:18-21` says "Uses Kubernetes DNS for service
    discovery: http://user-service:8080", but the `@FeignClient` `url` attribute is
    `${api.gateway.base-url}/api/v1/user` — it goes through the gateway, exactly like
    every other client in this module, not direct k8s DNS. Severity: **low** (misleading
    comment only).
22. **Test scaffolding is dead weight.** `BaseIntegrationTest.java` and
    `application-test.yml` configure Testcontainers Postgres + JPA `ddl-auto` for a
    module with no JPA starter, no entities, and zero test classes that extend the base
    class. See Tests. Severity: **low**.
23. **`/products` silently degrades for anonymous users instead of redirecting.**
    `ProductController.products()` (`.../controller/ProductController.java:30-39`) is the
    only "browse" page that checks `sessionManager.isAuthenticated(request)` — but
    instead of redirecting to `/login` like every other authenticated route in this
    module, it renders `product/list` with an empty product list. This is inconsistent
    with the rest of the module's auth pattern (redirect-to-login) and silently hides all
    products from anonymous visitors with no explanation in the UI. Severity: **low**.
24. **Several frontend DTOs carry fields with no upstream counterpart — always null in
    practice.** See "DTO field parity vs upstream services" above for the full list
    (`OrderItemDto.productName/variationName/totalPrice`,
    `OrderDto.subtotal/discountTotal/taxTotal/totalAmount`,
    `ProductDto.variations/minPrice/maxPrice/active`,
    `PaymentRequestDto.cardNumber/cardHolderName/expiryDate/cvv`,
    `PaymentResponseDto.failureReason`, `UserDto.createdAt/updatedAt`). Templates that
    render these fields directly (e.g. `product/detail.html` uses hardcoded `$29.99`
    rather than `product.minPrice`, so this is partly masked in the UI, but
    `order/orders.html`'s reliance on `order.totalPrice` — itself only rescued by the
    client-side `getTotalPrice()` computation — is a direct symptom of the
    `OrderDto.totalAmount` gap). Severity: **medium** (silent data loss, not a crash).
25. **Open design note not yet implemented:** `.github/issues/convert-address-htmx-event-based.md`
    proposes converting the address add/edit flow from "form returns the grid fragment
    directly" (current behavior, `hx-target="#address-grid" hx-swap="outerHTML"` on the
    form in `user/address-modal.html:14-16`) to an event-based pattern
    (`HX-Trigger` header + `hx-trigger="address-updated from:body"` on the grid, which
    `user/address-grid.html:2` already has wired up and waiting). The
    `// TODO: Convert to event-based pattern...` comments in `ProfileController.java`
    (lines 112-115, 148-149) mark the two handlers (`addAddressHtmx`, `editAddressHtmx`)
    that still need the change. Not a bug — an acknowledged, unimplemented refactor.
