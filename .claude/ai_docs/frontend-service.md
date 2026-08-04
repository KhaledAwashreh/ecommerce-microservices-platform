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
`http://localhost:8765`), never directly. `UserServiceClient`'s Javadoc used to claim
"Feign client... Uses Kubernetes DNS for service discovery" (see Gotchas) — fixed (GH #52) to
say it routes through the gateway like the module's other Feign clients, which is what all of
them actually do: every outbound call is a Spring Cloud OpenFeign client pointed at a gateway
URL — **not** `WebClient`. A dead `WebClientConfig` bean that contradicted this (unused
anywhere in the module) was removed — issue #51.

## Package layout

```
frontend-service/src/main/java/com/kawashreh/ecommerce/frontend/
├── FrontendApplication.java        # @SpringBootApplication, @EnableFeignClients(basePackages=".. .client")
├── client/                         # @FeignClient interfaces, one per upstream domain, all routed through the gateway
│   ├── AddressServiceClient.java   # -> ${gateway}/api/v1/address (served by user-service) — searchAddresses() added for GH #58, scoped to the caller (GH #59 removed the caller-supplied userId param); getAddresses() is likewise now scoped to the caller server-side (GH #64, previously returned every address for every user)
│   ├── CartServiceClient.java      # -> ${gateway}/api/v1/carts (order-service) — list/add/remove added for GH #13; updateItem/clearCart added for GH #6
│   ├── CategoryServiceClient.java  # -> ${gateway}/api/v1/categories (product-service)
│   ├── InventoryServiceClient.java # -> ${gateway}/api/v1/inventory (product-service)
│   ├── OrderServiceClient.java     # -> ${gateway}/api/v1/orders (order-service)
│   ├── PaymentServiceClient.java   # -> ${gateway}/api/v1/payment (payment-service)
│   ├── ProductServiceClient.java   # -> ${gateway}/api/v1/product (product-service)
│   └── UserServiceClient.java      # -> ${gateway}/api/v1/user (user-service)
├── config/
│   ├── BearerTokenInterceptor.java # feign.RequestInterceptor — attaches session JWT as "Authorization: Bearer <token>" to every Feign call
│   └── SessionManager.java         # thin wrapper over HttpSession: store/get JWT + username, isAuthenticated, invalidate
├── controller/                     # @Controller (not @RestController) classes returning Thymeleaf view names
│   ├── AuthController.java         # /, /login, /register, /logout
│   ├── CartController.java         # /cart, /cart/add, /cart/remove wired to CartServiceClient (GH #13); /cart/update and /checkout/place wired for GH #6 (checkout creates an order via OrderFacade, then clears the cart); GH #58 made /checkout and /checkout/place resolve and validate a real shipping address instead of discarding it
│   ├── InventoryController.java    # /inventory, /inventory/{id}
│   ├── OrderController.java        # /orders, /orders/{id}, POST /orders
│   ├── ProductController.java      # /products, /products/{id}, /products/search
│   └── ProfileController.java      # /profile, /profile/edit, /addresses/**
├── dto/                             # HTTP-facing DTOs shared with upstream services (plain POJOs, Lombok @Data/@Builder)
│   ├── AddressDto, CartDto, CartItemDto, CategoryDto, InventoryDto, OrderDto, OrderItemDto,
│   │   PaymentRequestDto, PaymentResponseDto, ProductDto, ProductVariationDto, UserDto
│   ├── facade/                      # composite view-model DTOs built by the facade layer
│   │   ├── OrderWithDetailsDto      # {order: OrderDto, payment: PaymentResponseDto}
│   │   ├── ProductWithDetailsDto    # {product: ProductDto, category: CategoryDto}
│   │   └── ProfileWithAddressesDto  # {user: UserDto, addresses: List<AddressDto>}
│   └── request/                     # inbound form-bound / request DTOs
│       ├── AddressRequest, UserLoginRequest, UserRegisterRequest, UserUpdateRequest
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
├── application.yml       # port 3000, thymeleaf cache=false, api.gateway.base-url, zipkin, actuator (jwt.secret/expiration removed — was dead config, see Gotchas)
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
- `BaseIntegrationTest.java` and `src/test/resources/application-test.yml` (a Postgres/JPA
  Testcontainers scaffold with no matching JPA starter or entities) were removed outright
  (GH #45) rather than wired up — see Tests.

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
| POST | `/cart/update` | CartController | `redirect:/cart` (or `redirect:/cart?error=...` via `GlobalExceptionHandler` on Feign failure) | `CartServiceClient.updateItem(userId, itemId, CartItemUpdateRequest)` — wired for GH #6 | Yes |
| GET | `/checkout` | CartController | `cart/checkout` | Checkout form — line items/totals are still hardcoded sample data (unrelated pre-existing gap, see Gotchas), but the shipping-address `<select>` is now populated from `ProfileFacade.getAddressesForUser(userId)` (GH #58), which resolves `userId` the same way `/cart` does. Reads `error` query param into the model | Yes |
| POST | `/checkout/place` | CartController | `redirect:/orders/{id}` on success, `redirect:/checkout?error=...` on failure | Builds an `OrderDto` from the caller's cart items and calls `OrderFacade.createOrder` (order-service's plain `POST /api/v1/orders`, **not** `createOrderFromCart` — see order-service ai_doc, that method is dead/broken). Clears the cart via `CartServiceClient.clearCart` on success. Does **not** call payment-service — payment orchestration is GH #9, a deliberate seam; `paymentMethod` is still accepted-but-unused, now with an explicit comment at the point it's read explaining that payment-service is being redesigned separately. GH #58: `addressId` is parsed as a `UUID`, rejected with `redirect:/checkout?error=...` if null/blank/unparseable, checked against `ProfileFacade.getAddressesForUser(userId)` (rejected the same way if it doesn't belong to the caller), and threaded through to order-service as `OrderDto.shippingAddressId`. Wired for GH #6, address handling added for GH #58 | Yes |
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
**(Fixed for GH #36)** `extractMessage` (`exception/GlobalExceptionHandler.java`) used to
assume every Feign error body was `common.ErrorResponse`'s shape, which only holds for
user-service — order-service/product-service/payment-service have no
`GlobalExceptionHandler` of their own and fall back to Spring Boot's default error body
(different shape, and a timestamp format `ErrorResponse`'s constructor can't parse), and
`api-gateway`'s fallback controller returns plain text. The real message from those was
silently discarded and replaced with a generic "Service error". It now tries
`common.ErrorResponse` first, then falls back to reading just the `"message"` key
generically out of whatever JSON came back (still returning a per-status default like
"Resource not found" if no usable message is found anywhere, rather than always
"Service error"). See `exception/GlobalExceptionHandlerTest.java`.

| Client method | Gateway path called | Owning service (per root `CLAUDE.md`) | Used by |
|---|---|---|---|
| `UserServiceClient.register` | `POST /api/v1/user/register` | user-service | `AuthController.register` |
| `UserServiceClient.login` | `POST /api/v1/user/login` | user-service | `AuthController.login` |
| `UserServiceClient.getUserById` | `GET /api/v1/user/{userId}` | user-service | `ProfileFacade.getUserById` (facade method itself is unused externally — see Gotchas) |
| `UserServiceClient.getUserByUsername` | `GET /api/v1/user?username=` | user-service | `ProfileFacade.getUserByUsername` / `getProfileWithAddresses` — used by OrderController, ProfileController |
| `UserServiceClient.updateUser` | `PUT /api/v1/user/{userId}` (header `X-User-ID`) | user-service | `ProfileController.updateProfile` |
| `AddressServiceClient.getAddresses` | `GET /api/v1/address` | user-service | `ProfileFacade.getAllAddresses` / `getProfileWithAddresses` — GH #64 fix: server-side now scopes to the caller's own `X-User-ID` (previously returned every address for every user); no client change was needed since the header is populated from the verified JWT by user-service's `JwtAuthFilter`, not sent explicitly by this client |
| `AddressServiceClient.searchAddresses` | `GET /api/v1/address/search` | user-service | `ProfileFacade.getAddressesForUser` — added for GH #58. Server-side scoped to the caller's own `X-User-ID` (GH #59 removed the earlier caller-supplied `userId` query param). Used by `CartController` both to populate the checkout address selector and to validate that a submitted `addressId` actually belongs to the caller |
| `AddressServiceClient.getAddressById` | `GET /api/v1/address/{id}` | user-service | `ProfileController.addressModal` |
| `AddressServiceClient.createAddress` | `POST /api/v1/address` (header `X-User-ID`) | user-service | `ProfileController.addAddressHtmx` / `addAddress` |
| `AddressServiceClient.updateAddress` | `PUT /api/v1/address/{id}` (header `X-User-ID`) | user-service | `ProfileController.editAddressHtmx` |
| `AddressServiceClient.deleteAddress` | `DELETE /api/v1/address/{id}` (header `X-User-ID`) | user-service | `ProfileController.deleteAddress` |
| `CartServiceClient.getCartForUser` | `GET /api/v1/carts/user/{userId}` | order-service | `CartController.cart` — added for GH #13 |
| `CartServiceClient.addItem` | `POST /api/v1/carts/user/{userId}/items` | order-service | `CartController.addToCart` — added for GH #13 |
| `CartServiceClient.removeItem` | `DELETE /api/v1/carts/user/{userId}/items/{itemId}` | order-service | `CartController.removeFromCart` — added for GH #13 |
| `CartServiceClient.updateItem` | `PUT /api/v1/carts/user/{userId}/items/{itemId}` | order-service | `CartController.updateCartItem` — added for GH #6 |
| `CartServiceClient.clearCart` | `DELETE /api/v1/carts/user/{userId}` | order-service | `CartController.placeOrder`, called after a successful checkout — added for GH #6 |
| `ProductServiceClient.getAllProducts` | `GET /api/v1/product` | product-service | `ProductFacade.getAllProducts` / `searchProducts` |
| `ProductServiceClient.getProductById` | `GET /api/v1/product/{id}` | product-service | `ProductFacade.getProductWithDetails` |
| `CategoryServiceClient.getAllCategories` | `GET /api/v1/categories` | product-service | `ProductFacade.getAllCategories` (facade method itself is unused externally — no controller calls it) |
| `CategoryServiceClient.getCategoryById` / `getCategoryByName` | `GET /api/v1/categories/{id}` / `/name/{name}` | product-service | **Nobody** — unused |
| `InventoryServiceClient.getInventoryByVariation` | `GET /api/v1/inventory/product-variation/{id}` | product-service | `InventoryController.inventoryDetail` |
| `InventoryServiceClient.checkAvailability` | `GET /api/v1/inventory/product-variation/{id}/availability` | product-service | **Nobody** — unused |
| `InventoryServiceClient.deductStock` / `restoreStock` | `PUT /api/v1/inventory/product-variation/{id}/deduct`/`/restore` | product-service | **Nobody** — unused |
| `OrderServiceClient.createOrder` | `POST /api/v1/orders` | order-service | `OrderFacade.createOrder`, called from `CartController.placeOrder` (`POST /checkout/place`, wired for GH #6) and from the still-unreachable `OrderController.createOrder` (see Gotchas) |
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

**(Fixed for GH #34)** The fields below used to exist on the frontend DTO with **no
counterpart** on the corresponding upstream service's DTO, so they always deserialized
as `null`/`false` from a real gateway response — silent data loss the UI rendered as
blanks instead of failing. Per field, the fix direction was "the upstream service
should provide it" or "the frontend should stop declaring it"; since none of these
fields were read by any template/controller in this module (checked directly — one
exception below), and adding them upstream would have been a cross-service DTO change
outside this issue's scope, they were removed from the frontend DTO. A regression test,
`dto/DtoUpstreamParityTest.java`, asserts each frontend DTO's field set is a subset of
its upstream counterpart's, to catch this class of drift going forward (this is the
"contract-auditor" case the issue called out).

| Frontend DTO | Removed field(s) (had no upstream counterpart) | Upstream DTO checked |
|---|---|---|
| `dto/OrderItemDto.java` | `productName`, `variationName`, `totalPrice` | `order-service/.../application/dto/OrderItemDto.java` (has only `id, productSku, quantity, unitPrice, createdAt, updatedAt, createdBy, updatedBy`) |
| `dto/OrderDto.java` | `subtotal`, `discountTotal`, `taxTotal`, `totalAmount` | `order-service/.../application/dto/OrderDto.java` (has no such fields; has `discountsApplied`, which frontend's `OrderDto` still lacks) |
| `dto/ProductDto.java` | `variations`, `minPrice`, `maxPrice`, `active` | `product-service/.../application/dto/ProductDto.java` (stops at `thumbnailUrl`) |
| `dto/PaymentRequestDto.java` | `cardNumber`, `cardHolderName`, `expiryDate`, `cvv` | `payment-service/.../application/dto/PaymentRequestDto.java` (has only `orderId, buyerId, amount, paymentMethod`) — moot in practice since `PaymentServiceClient.processPayment` has no callers (see Gotchas) |
| `dto/PaymentResponseDto.java` | `failureReason` | `payment-service/.../application/dto/PaymentResponseDto.java`. **Exception**: this one *was* read, by `order/detail.html`'s "Failure Reason" block (`payment.failureReason ?: 'Unknown error'`). Since the field was always null in practice (this bug), that Elvis fallback always fired anyway — the template now renders the same static "Unknown error" text directly instead of reading a field that never carried real data. If a real failure reason is wanted here later, it needs to be added to payment-service's `PaymentResponseDto` first (a separate, cross-service change). |
| `dto/UserDto.java` | `createdAt`, `updatedAt` | upstream user-service HTTP-facing `UserDto` has no such fields (the internal `domain/service/dto/UserResponse` does, but that's not what crosses the wire to this module) |

`OrderDto.getTotalPrice()` (`dto/OrderDto.java`) still exists and is unaffected — it
derives a total client-side from `selectedItems`, which is a distinct, deliberate
workaround for the (still-real) `totalAmount`/`subtotal` gap, not something this fix
removed.

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
- `jwt.secret`/`jwt.expiration` used to be present in `application.yml` but were confirmed
  unused by any class in this module and removed (see Gotchas) — this module never signed
  or parsed JWTs; it only ever forwarded the opaque token it received from `user-service`.

## Tests

Two plain-Mockito unit test classes exist under
`src/test/java/.../controller/` — `OrderControllerTest` (regression coverage for the
`order/orders.html` nesting bug, see Gotchas) and `CartControllerTest` (added for GH #6:
covers `/cart/update` and `/checkout/place`, including the empty-cart, order-creation-failed,
and unparseable-product-SKU failure paths). GH #58 extended `CartControllerTest` with
`ProfileFacade.getAddressesForUser` stubbing on every `placeOrder` test that reaches past
address validation, plus new cases for null/blank/unparseable `addressId` and an address
that doesn't belong to the caller (each asserted to redirect to `/checkout?error=...`
without calling `OrderFacade.createOrder`), and the success-path test now also asserts the
submitted `OrderDto.shippingAddressId` matches the owned address used in the test. Both
test classes use `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks` — no
Spring context, no Docker, run via plain `mvn test`.

Note on this environment: this repo's pinned Mockito/ByteBuddy version does not support
JDK 25 (`Byte Buddy could not instrument all classes within the mock's type hierarchy` /
"Java 25 (69) is not supported"). Running these tests requires `JAVA_HOME` pointed at a
JDK 21 install (e.g. `C:\Program Files\Java\jdk-21` if present); this is an environment
tooling mismatch, not something GH #58 introduced or fixed.

`src/test/java/.../BaseIntegrationTest.java` and `src/test/resources/application-test.yml`
were **removed** (GH #45), not wired up: the abstract `@SpringBootTest` +
`@Testcontainers` base class spun up a `PostgreSQLContainer` and wired
`spring.datasource.*`/`spring.jpa.hibernate.ddl-auto`, but the module has no JPA starter
and no entities at all (see Persistence) — the scaffolding could never have exercised
anything even with a subclass, unlike payment-service's equivalent (real JPA/Postgres
usage), which got a real subclass instead. The two real test classes above
(`OrderControllerTest`, `CartControllerTest`) already provide this module's actual
coverage and don't need Docker/a Spring context at all.

## Gotchas

1. ~~Module is not WebClient-based, contradicting `WebClientConfig` and root `CLAUDE.md`.~~
   Fixed (issue #51): `config/WebClientConfig.java` defined a `WebClient` `@Bean` that no
   class anywhere in `src/main` injected or called — every outbound call goes through
   `@FeignClient` interfaces in `client/`, via `feign.RequestInterceptor`
   (`BearerTokenInterceptor`), not a `WebClient` filter. The dead bean has been removed
   and the root `CLAUDE.md` no longer references it.
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
6. **(Fixed for GH #6, address handling fixed for GH #58) Checkout form now has a handler,
   and now threads a real shipping address through.**
   `cart/checkout.html:23` (`<form action="/checkout/place" method="post">`) maps to
   `CartController.placeOrder` (`@PostMapping("/checkout/place")`). Until GH #58,
   `checkout.html`'s address `<select>` offered a single hardcoded, non-UUID
   `value="1"` option, and the handler read `addressId`/`paymentMethod` but used neither —
   an order could be placed with no shipping address at all and no error shown. That is
   fixed for `addressId`: the `<select>` is now populated from
   `ProfileFacade.getAddressesForUser(userId)` (real `AddressDto.id` UUIDs), and
   `placeOrder` parses the submitted value as a `UUID`, redirects to
   `/checkout?error=...` if it's null/blank/unparseable, re-fetches the caller's own
   addresses and redirects the same way if the id isn't among them (never trusts the
   submitted id), and otherwise threads it through `buildOrderFromCart` onto
   `OrderDto.shippingAddressId` (see order-service ai_doc for the corresponding
   domain/entity/DTO field). `paymentMethod` remains **intentionally** unused — it is
   accepted (payment-service is being redesigned separately, per the owner) with an
   inline comment at the parameter declaration explaining why, but is still not
   persisted or forwarded anywhere; payment invocation itself is still GH #9's separate
   concern. The handler builds the rest of the `OrderDto` from the caller's cart items and
   calls `OrderFacade.createOrder` (order-service's plain create path), then clears the
   cart via `CartServiceClient.clearCart` on success and redirects to `/orders/{id}`. On
   any failure (no/invalid/not-owned address, empty cart, unparseable cart item,
   order-service rejecting the request) it redirects to `/checkout?error=...` instead of
   letting an exception reach `GlobalExceptionHandler` — necessary because
   `resolveRedirectPath`'s `ACTION_VERBS` heuristic doesn't recognize `"place"` as the
   last path segment, so an uncaught exception here would redirect to a GET on
   `/checkout/place`, which has no `@GetMapping` and would 404.
7. **(Fixed for GH #6) Cart quantity-change form now has a handler.**
   `cart/cart.html:50` (`<form action="/cart/update" method="post">`, auto-submitted via
   `onchange="this.form.submit()"`) now maps to `CartController.updateCartItem`
   (`@PostMapping("/cart/update")`), which calls the new
   `CartServiceClient.updateItem(userId, itemId, CartItemUpdateRequest)` ->
   `PUT /api/v1/carts/user/{userId}/items/{itemId}` on order-service. Unlike the checkout
   handler, this one does **not** wrap the Feign call in its own try/catch — a failure
   propagates to `GlobalExceptionHandler`, whose `ACTION_VERBS` set already contains
   `"update"`, so it redirects to `/cart?error=...` correctly (both `/cart` and
   `/checkout` GET handlers now read the `error` query param into the model, and both
   templates render it in a red banner, matching the pattern already used by
   `auth/login.html`/`auth/register.html`).
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
9. **`OrderController.createOrder` (`POST /orders`) is still unreachable from any
   template** — it consumes `@RequestBody OrderDto` (JSON body) and no template POSTs
   JSON to `/orders`. `OrderFacade.createOrder` itself is no longer dead, though: GH #6's
   `CartController.placeOrder` calls it directly (Java-to-Java, not through this
   controller) to implement checkout. Severity: **low** (dead controller method, but the
   facade/client path it duplicates is now exercised elsewhere).
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
15. ~~`RoleServiceClient`, `RoleDto`, `RoleRequest` are entirely dead code.~~ Removed
    (issue #51) — no controller, facade, or template in this module referenced any of
    them; there is no role-management UI at all.
16. ~~`UserLoginDto` and `UserRegisterDto` (in `dto/`, not `dto/request/`) are dead
    duplicates.~~ Removed (issue #51) — the module uses `dto/request/UserLoginRequest`
    and `dto/request/UserRegisterRequest` for the same purpose.
17. **`InventoryServiceClient.checkAvailability`/`deductStock`/`restoreStock` and
    `CategoryServiceClient.getCategoryById`/`getCategoryByName` and
    `OrderServiceClient.getAllOrders` and `PaymentServiceClient.getPayment` are all
    unused.** No caller anywhere in this module. Severity: **low** (dead client methods).
18. **`ProfileFacade` swallows exceptions via `System.out.print` instead of a logger.**
    `.../facade/ProfileFacade.java:33,39` — `System.out.print(e.getMessage())` — every
    other facade/controller in the module uses SLF4J (`OrderController`,
    `ProductController` both declare a `Logger`); `ProfileFacade` does not, and doesn't
    even use `println` (no trailing newline). Severity: **low**.
19. ~~**`server.port` mismatch between `application.yml` (3000) and `Dockerfile`'s
    `EXPOSE`/`HEALTHCHECK` (8080).**~~ **Fixed (GH #52).** `application.yml:1-2` sets
    `server.port: 3000`; `Dockerfile`'s `HEALTHCHECK`/`EXPOSE` now both target `3000` too,
    matching the port the app actually binds (previously both said `8080`, so the in-image
    healthcheck was always probing a closed port).
20. **`jjwt-api`/`jjwt-impl`/`jjwt-jackson` Maven dependencies are unused.** No class in
    `frontend-service/src/main` imports `io.jsonwebtoken` — the module treats the token
    from `user-service` as an opaque string. (The `jwt.secret`/`jwt.expiration`
    `application.yml` keys that were the config-side half of this dead-code smell have been
    removed — they duplicated the real `api-gateway`/`user-service` signing secret for no
    reason, since nothing here ever read them.) Dead dependencies remain. Severity: **low**.
21. ~~**`UserServiceClient`'s Javadoc is stale/misleading.**~~ **Fixed (GH #52).**
    `.../client/UserServiceClient.java:18-21` used to say "Uses Kubernetes DNS for service
    discovery: http://user-service:8080", but the `@FeignClient` `url` attribute is
    `${api.gateway.base-url}/api/v1/user` — it goes through the gateway, exactly like
    every other client in this module, not direct k8s DNS. The Javadoc now says so.
22. ~~**Test scaffolding is dead weight.**~~ — fixed (GH #45): `BaseIntegrationTest.java`
    and `application-test.yml` were removed rather than wired up, since neither could
    ever exercise anything real in a module with no JPA starter/entities. See Tests.
23. **`/products` silently degrades for anonymous users instead of redirecting.**
    `ProductController.products()` (`.../controller/ProductController.java:30-39`) is the
    only "browse" page that checks `sessionManager.isAuthenticated(request)` — but
    instead of redirecting to `/login` like every other authenticated route in this
    module, it renders `product/list` with an empty product list. This is inconsistent
    with the rest of the module's auth pattern (redirect-to-login) and silently hides all
    products from anonymous visitors with no explanation in the UI. Severity: **low**.
24. **(Fixed for GH #34) Frontend DTOs no longer carry fields with no upstream
    counterpart.** See "DTO field parity vs upstream services" above — the fields were
    removed rather than backfilled upstream, since none were actually consumed by any
    template/controller in this module except `PaymentResponseDto.failureReason`, whose
    one reader (`order/detail.html`) always rendered its Elvis fallback anyway (the field
    was always null). `order/orders.html`'s reliance on `order.totalPrice` is unaffected —
    that's `OrderDto.getTotalPrice()`, a computed method, not one of the removed fields;
    the underlying `OrderDto.totalAmount` gap (no upstream field, hence the client-side
    workaround) still exists and was out of scope for this fix.
25. **Open design note not yet implemented:** `.github/issues/convert-address-htmx-event-based.md`
    proposes converting the address add/edit flow from "form returns the grid fragment
    directly" (current behavior, `hx-target="#address-grid" hx-swap="outerHTML"` on the
    form in `user/address-modal.html:14-16`) to an event-based pattern
    (`HX-Trigger` header + `hx-trigger="address-updated from:body"` on the grid, which
    `user/address-grid.html:2` already has wired up and waiting). The
    `// TODO: Convert to event-based pattern...` comments in `ProfileController.java`
    (lines 112-115, 148-149) mark the two handlers (`addAddressHtmx`, `editAddressHtmx`)
    that still need the change. Not a bug — an acknowledged, unimplemented refactor.
26. **order-service's `OrderDto`/`OrderItemDto` mark several fields `@NonNull`
    (Lombok), including `id` — any future caller that posts to `POST /api/v1/orders` with
    those fields absent/null will get an `HttpMessageNotReadableException` (400) from
    Jackson invoking the Lombok-generated setter with `null`, not a validation error.
    Neither this module nor order-service sets
    `spring.jackson.default-property-inclusion`, so Jackson serializes nulls by default —
    a frontend DTO with an unset field silently becomes `"field": null` on the wire.
    `CartController.buildOrderFromCart` (GH #6) populates every `@NonNull` field
    explicitly for this reason, including generating a client-side `id` (`UUID.randomUUID()`)
    for the order and every order item, even though the id is conceptually server-assigned
    — `OrderEntity.id` uses `@GeneratedValue(strategy = GenerationType.UUID)`, which
    accepts a pre-set id and still inserts a new row, so this works but is a slightly odd
    contract. Any other future caller of this endpoint needs the same treatment.
    `order-service/.../application/dto/OrderDto.java`,
    `order-service/.../application/dto/OrderItemDto.java`.
