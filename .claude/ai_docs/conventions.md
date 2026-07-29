# Conventions

Derived from reading every module's source tree, not aspirational. See "Known deviations"
for where a module breaks its own module's pattern.

## Package layering

The documented four-layer split (`CLAUDE.md`) is real and followed by all five backend
services and `frontend-service` uses a flatter variant (see below):

```
application/      controller, dto, mapper (HTTP <-> domain), application service (product-service only)
domain/           model (POJOs), service + service.impl, enums, exception
dataAccess/       entity (JPA), repository/dao, mapper (entity <-> domain)
infrastructure/   cache config, http clients (Feign/WebClient), security, config
constants/        ApiPaths, CacheConstants, JwtConstants
```

Verified per module:
- `user-service`, `order-service`, `payment-service`: exactly this tree, lowercase package
  names throughout.
- `product-service`: same tree but `infastructure/` (misspelled, not `infrastructure/`) and
  `dataAccess/Dao/` (capitalized `Dao`, not `repository`). Additionally carries a fifth,
  unlisted package `infra/models/` containing a single class
  (`product-service/src/main/java/.../infra/models/Attachment.java`) — a third,
  inconsistent spelling of "infrastructure" in the same module. `product-service` is also the
  only module with an `application/service/` sublayer
  (`ProductApplicationService`, `ReviewApplicationService`) sitting between controllers and
  `domain/service` — an extra indirection not present in any other backend service.
- `api-gateway`: package is `Infrastructure/` (capital I) —
  `api-gateway/src/main/java/com/kawashreh/ecommerce/api_gateway/Infrastructure/`. Every other
  module uses lowercase `infrastructure/`. `api-gateway` also has no `dataAccess/` or
  `domain/` layer (it's stateless, routing-only) — its own root package contains
  `FallbackController.java` directly, not under `application/`.
- `frontend-service`: does not use this layering at all. Its packages are flat:
  `client/` (Feign interfaces), `config/`, `controller/`, `dto/` (+ `dto/facade/`, `dto/request/`),
  `exception/`, `facade/`. No `domain/`, `dataAccess/`, or `constants/` package — it has no
  persistence layer and no `ApiPaths` constants class (routes are hardcoded strings in
  `@GetMapping`/`@PostMapping` annotations across its controllers).

Do not "fix" the `product-service` misspellings or `api-gateway` capitalization casually —
the rename touches imports across the module (explicitly called out in root `CLAUDE.md` for
`product-service`; the same caution applies to `api-gateway`'s `Infrastructure/`).

## Domain model vs. JPA entity, and the two mapper layers

Every backend service keeps domain POJOs (`domain/model/*.java`, plain classes, typically with
a `@Builder`-style static factory or Lombok `@Builder`) strictly separate from JPA entities
(`dataAccess/entity/*Entity.java`, annotated with `@Entity`/`@Table`/`@Id`). Two distinct
mapper layers convert between representations, and they are not interchangeable:

| Mapper layer | Location | Converts | Example |
|---|---|---|---|
| Entity mapper | `dataAccess/mapper/*Mapper.java` | JPA entity <-> domain model | `OrderMapper.toEntity(Order)` / `OrderMapper.toDomain(OrderEntity)` (`order-service/src/main/java/.../dataAccess/mapper/OrderMapper.java`) |
| HTTP mapper | `application/mapper/*HttpMapper.java` | HTTP DTO <-> domain model | `OrderHttpMapper.toDto(Order)` / `OrderHttpMapper.toDomain(OrderDto)` (`order-service/src/main/java/.../application/mapper/OrderHttpMapper.java`) |

Controllers only ever see DTOs and call `*HttpMapper`; services only ever see domain models
and call `*Mapper` (entity side) internally. This chain holds in `order-service`,
`user-service`, `product-service`, `payment-service`. Mappers observed are static-method
utility classes (not Spring beans, not MapStruct) — plain `public static X toY(Z)` methods.

## Naming patterns

| Pattern | Purpose | Example |
|---|---|---|
| `constants/ApiPaths.java` | Centralizes all route path strings for a module. Present in every backend service and `api-gateway`; **absent** from `frontend-service` (see above). | `order-service/src/main/java/.../constants/ApiPaths.java` |
| `constants/CacheConstants.java` | Cache name constants for `@Cacheable`/`@CacheEvict`. Only present where caching is used: `user-service`, `product-service`. `order-service`, `payment-service`, `api-gateway` do not define one (api-gateway has a `CacheConfig` but no matching `CacheConstants`). | `user-service/src/main/java/.../constants/CacheConstants.java` |
| `constants/JwtConstants.java` | Token expiration constant only now (the signing secret was moved to `jwt.secret`/`JWT_SECRET`, no committed default). Only in `api-gateway` and `user-service` — the two services that actually parse/sign tokens in Java. | `user-service/src/main/java/.../constants/JwtConstants.java` |
| `*Dto` | HTTP-facing request/response shape, one per resource, under `application/dto/`. | `OrderDto`, `CartItemDto` |
| `*Entity` | JPA-mapped class, one per table, under `dataAccess/entity/`. | `OrderEntity`, `PaymentEntity` |
| `*ServiceImpl` | Concrete implementation of a `domain/service/*Service` interface, under `domain/service/impl/`. Every backend service follows interface + `Impl` for its domain services; `api-gateway` and `frontend-service` have no domain service layer. | `OrderServiceImpl implements OrderService` |
| `*HttpMapper` | `application/mapper` DTO<->domain converter (see above). | `OrderHttpMapper`, `PaymentHttpMapper` |
| `*Mapper` (no `Http`) | `dataAccess/mapper` entity<->domain converter. | `OrderMapper`, `PaymentMapper` |

IDs are `java.util.UUID` everywhere (entities, domain models, DTOs, path variables) — no
service uses `Long`/auto-increment identity for its primary business entities.

## Error handling

`common/src/main/java/com/kawashreh/ecommerce/common/dto/ErrorResponse.java` is the shared
error DTO: `{status: int, message: String, timestamp: LocalDateTime}`. `common` also defines
three custom exceptions (`common/src/main/java/.../exceptions/`):
`DuplicateEntityException`, `NoSuchElementException` (shadows `java.util.NoSuchElementException`
by name — watch imports), `IllegalArgumentException` (same shadowing risk).

**Only two modules actually implement a `GlobalExceptionHandler`**:
- `user-service/src/main/java/.../exception/GlobalExceptionHandler.java` —
  `@RestControllerAdvice`, maps `NoSuchElementException` -> 404,
  `MethodArgumentNotValidException` -> 400, `DuplicateEntityException` -> 409, catch-all
  `Exception` -> 500 with a generic message. Returns `ErrorResponse` as JSON.
- `frontend-service/src/main/java/.../exception/GlobalExceptionHandler.java` —
  `@ControllerAdvice` (not REST — this app serves HTML), catches `FeignException` and
  extracts the upstream `ErrorResponse.message` when present, otherwise maps status codes to
  generic text (404/409/400). Redirects back to the originating page with `?error=<message>`
  rather than rendering a JSON body, using an `ACTION_VERBS` heuristic to strip the last path
  segment (`add`/`delete`/`remove`/`create`/`update`/`edit`) so a POST to `/cart/add` redirects
  to `/cart`, not `/cart/add`.

`order-service`, `product-service`, `payment-service`, and `api-gateway` have **no**
`GlobalExceptionHandler`. Unhandled exceptions in those services fall through to Spring Boot's
default `/error` handling (or, in `api-gateway`'s reactive stack, to whatever
`WebExceptionHandler` Spring Cloud Gateway installs by default) — they do not produce the
shared `ErrorResponse` shape. `order-service`'s `OrderServiceImpl` deliberately wraps failures
in a bare `RuntimeException` with a descriptive message (e.g. "Inventory update failed...")
that, with no handler present, surfaces as a generic 500 with no structured body.

## Caching

Redis-backed, `@EnableCaching` + `RedisCacheManager`, present in `user-service`,
`product-service`, `order-service` (has a `CacheConfig` but no `CacheConstants`, see above),
and `api-gateway`. Pattern (identical across `CacheConfig` classes, e.g.
`user-service/src/main/java/.../infrastructure/cache/CacheConfig.java`):
- A Jackson `ObjectMapper` with `JavaTimeModule` and polymorphic default typing
  (`NON_FINAL`, `JsonTypeInfo.As.PROPERTY`) feeds a `GenericJackson2JsonRedisSerializer`.
- `RedisCacheConfiguration`: `disableCachingNullValues()`, `entryTtl(Duration.ofMinutes(10))`
  (10-minute TTL is the fixed default across every module that configures it — no
  per-cache-name TTL override was found anywhere).
  String key serializer, JSON value serializer.
- Also exposes a raw `RedisTemplate<String, Object>` and a `StringRedisTemplate` bean
  alongside the cache manager, for manual Redis access outside `@Cacheable`.
- Cache names/keys are declared as `public static final String` constants in
  `CacheConstants` (e.g. `USERS_BY_ID`, `USERS_BY_EMAIL`, `USER_BY_USERNAME`,
  `ADDRESS_BY_ID` in `user-service`) and referenced from `@Cacheable("...")` on domain
  service methods (not verified for every method — cite per-module doc for exact
  key/eviction points).

## Testing conventions

Every backend service that has integration tests defines its own
`BaseIntegrationTest` (no shared parent in `common`) under
`<module>/src/test/java/.../BaseIntegrationTest.java`:
`api-gateway`, `frontend-service`, `payment-service`, `product-service`, `user-service`. No
`BaseIntegrationTest` was found for `order-service`.

Shared shape across all of them:
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")...
    @DynamicPropertySource static void configureProperties(DynamicPropertyRegistry registry) { ... }
    @AfterAll static void cleanup() { ... }
}
```
- `spring.jpa.hibernate.ddl-auto` is force-set to `create-drop` for tests via
  `@DynamicPropertySource`, overriding whatever the `test` profile's `application-test.yml`
  says — schema is entirely regenerated per test run, no migration scripts involved.
- `user-service`'s variant additionally spins up a `GenericContainer<>("redis:7-alpine")` and
  wires `spring.data.redis.host`/`port` dynamically — the only `BaseIntegrationTest` that
  containerizes Redis (matches it being the module with the richest caching surface).
  `product-service`'s does not container ize Redis despite `product-service` also having a
  `CacheConfig` — its tests presumably run with caching effectively disabled or failing over
  (not verified further; flagged as worth checking when touching `product-service` caching
  tests).
- `postgres` container always uses username/password `test`/`test`, independent of the
  `test1234` convention used everywhere else in dev config.
- Concrete integration test classes extend `BaseIntegrationTest` and rely on Testcontainers,
  so a running Docker daemon is required to run them (`mvn clean verify` will fail without
  one, matching the root `CLAUDE.md` note).

## Known deviations, by module

| Module | Deviation | File |
|---|---|---|
| `product-service` | Package misspelled `infastructure/` instead of `infrastructure/`. | `product-service/src/main/java/.../infastructure/` |
| `product-service` | `dataAccess/Dao/` capitalized, elsewhere it's `repository/` or `dao/` (lowercase). | `product-service/src/main/java/.../dataAccess/Dao/` |
| `product-service` | Third infra spelling: `infra/models/Attachment.java`, neither `infrastructure` nor `infastructure`. | `product-service/src/main/java/.../infra/models/Attachment.java` |
| `product-service` | Extra `application/service/` layer not present in any other module. | `product-service/src/main/java/.../application/service/ProductApplicationService.java` |
| `product-service` | No `GlobalExceptionHandler`. | n/a |
| `api-gateway` | Package capitalized `Infrastructure/` instead of `infrastructure/`. | `api-gateway/src/main/java/.../Infrastructure/` |
| `api-gateway` | No `GlobalExceptionHandler`; two overlapping auth layers (`SecurityConfig` + `JwtAuthFilter`) instead of one. | `api-gateway/src/main/java/.../Infrastructure/configuration/SecurityConfig.java` |
| `payment-service` | No `GlobalExceptionHandler`, no `CacheConstants`/caching at all. | n/a |
| `order-service` | No `GlobalExceptionHandler`, no `CacheConstants`, no `BaseIntegrationTest`. | n/a |
| `order-service` | Unused `PaymentClient` Feign interface — never injected, never called. | `order-service/src/main/java/.../infrastructure/http/client/PaymentClient.java` |
| `order-service` | `restoreInventory` Feign method exists but has zero callers — the compensating-transaction path is incomplete. | `order-service/src/main/java/.../infrastructure/http/client/ProductServiceClient.java:33-36` |
| `frontend-service` | No `domain/`, `dataAccess/`, or `constants/ApiPaths` layer — architecturally the odd one out among the six modules. | n/a |
| `frontend-service` | `CartController` add/remove/cart endpoints are stubs — no cart persistence call, `cartItems` is always `Collections.emptyList()`. | `frontend-service/src/main/java/.../controller/CartController.java` |
| `api-gateway`, `user-service` | JWT signing secret (`jwt.secret`) is set independently per service via `JWT_SECRET`, rather than shared config — the two can still drift if configured inconsistently, though no longer via a copy-pasted source literal (that was fixed; see `api-gateway.md`/`user-service.md` Security). | `api-gateway/src/main/resources/application.yml`, `user-service/src/main/resources/application.yml` |
