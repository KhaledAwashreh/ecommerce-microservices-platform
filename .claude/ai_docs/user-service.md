# user-service

> **Amendment (GH #17/#18/#19 fix):** this doc predates the fix for those three P0
> security issues and several statements below ("does not itself enforce JWT-based
> authorization", "no `X-User-ID` check, no admin check" on RoleController, "trusts the
> `X-User-ID` header... verbatim") are now **stale**. Current state: a
> `JwtAuthFilter`/`JwtService` pair (`infrastructure/security/`) now validates every
> request's bearer token locally before it reaches a controller (except `/register`,
> `/login`, `/actuator/**`). On success it overwrites `X-User-ID`/`X-User-Name`/
> `X-User-Role` on the request with values derived from the token's verified claims, so
> those headers can no longer be spoofed by a caller — `UserController`/
> `AddressController`'s existing `@RequestHeader("X-User-ID")` reads now get a
> trustworthy value for free. `RoleController.create`/`delete` now require
> `X-User-Role == ADMIN` (`domain/enums/UserRole`), returning 403 otherwise. Tokens
> issued by `login()` now carry `userId` and `role` claims (previously subject/username
> only) so every backend service can derive identity independently. The rest of this
> document (package layout, endpoint tables minus the Authorization column notes above,
> gotchas list) is otherwise still accurate as of the fix; it has not been fully
> regenerated.

## Purpose

Owns the User/Account/Address/Role domain for the platform: registration, login (JWT
issuance), profile CRUD, address book management, and a role catalog (name +
free-form permissions string). It is a plain Spring Boot 3 / Java 21 REST service with
a Postgres-backed JPA persistence layer and a Redis read-through cache. It does not
itself enforce JWT-based authorization — it trusts the `X-User-ID` header that the
API gateway's `JwtAuthFilter` attaches after validating the token
(`user-service/src/main/java/.../application/controller/UserController.java`,
`AddressController.java`).

## Package layout

```
com.kawashreh.ecommerce.user_service
├── UserServiceApplication.java          @SpringBootApplication, @EnableTransactionManagement
├── application/
│   ├── controller/                      UserController, AddressController, RoleController
│   ├── dto/                             HTTP-facing request/response DTOs
│   └── mapper/                          UserHttpMapper, AddressHttpMapper, RoleHttpMapper (HTTP DTO <-> domain-service DTO)
├── constants/
│   ├── ApiPaths.java                    BASE_PATH="/api/v1/user", REGISTER, LOGIN only
│   ├── CacheConstants.java              cache names
│   └── JwtConstants.java                hardcoded HMAC secret + 30-min expiry
├── dataAccess/
│   ├── entity/                          UserEntity, AccountEntity, AddressEntity, RoleEntity (JPA)
│   ├── mapper/                          UserMapper, AccountMapper, AddressMapper, RoleMapper (entity <-> domain model)
│   └── repository/                      Spring Data JPA repositories
├── domain/
│   ├── enums/                           AccountStatus, AccountType, Gender, UserRole (unused)
│   ├── model/                           User, Account, Address, Role (plain POJOs, business methods)
│   └── service/ + service.impl/         UserService, AddressService, RoleService + impls; service/dto holds the internal request/response shapes
├── exception/
│   ├── GlobalExceptionHandler.java      @RestControllerAdvice
│   └── MethodArgumentNotValidException.java   dead: shadows Spring's own class, never thrown/caught
└── infrastructure/
    ├── cache/CacheConfig.java           RedisCacheManager + RedisTemplate + StringRedisTemplate beans
    └── security/                        JwtService, Argon2PasswordHasher, PasswordConfig, PasswordHasher
```

No `constants/ApiPaths.java` entries exist for Address/Role routes — `AddressController`
and `RoleController` hardcode `"/api/v1/address"` and `"/api/v1/roles"` directly in
`@RequestMapping`, deviating from the repo-wide convention that all paths live in
`ApiPaths` (`CLAUDE.md` "Conventions").

## Domain model

| Domain POJO (`domain/model`) | JPA entity (`dataAccess/entity`) | Mapper | Notes |
|---|---|---|---|
| `User` | `UserEntity` (table `"user"`, quoted — reserved word) | `UserMapper` | Has `account` (1:1), `addresses` (1:N), `role` (N:1). Business methods: `addAddress`, `updateEmail`, `getDefaultAddress`, `setDefaultAddress`, `changePassword`, `checkPassword`, `isAdmin`/`isCustomer`/`isSeller` (string-compare against `role.getName()`, not the `UserRole` enum). |
| `Account` | `AccountEntity` (table `Account`) | `AccountMapper` | 1:1 with User, holds `hashedPassword`, `accountStatus`, `accountType`, `activated`/`archived`/`emailVerified`/`phoneVerified`, `locale`, `timeZone`. `activate()` and `canLogin()` business methods exist but `canLogin()` is never called by any service (see Gotchas). |
| `Address` | `AddressEntity` (table `Address`) | `AddressMapper` | N:1 to User. `AddressEntity` field is named `DefaultAddress` (capitalized, deviates from `defaultAddress` used everywhere else — Lombok builder method is literally `.DefaultAddress(...)`, see `AddressMapper.java:13`). |
| `Role` | `RoleEntity` (table `roles`) | `RoleMapper` | `permissions` is a raw `String` (column `jsonb default '[]'`) — no structured type, no validation that it is valid JSON. |

`domain/enums/UserRole` (`ADMIN`/`SELLER`/`CUSTOMER`) is declared but never referenced
anywhere in `user-service/src/main` or `src/test` — dead code. Role membership is
actually resolved via `Role.name` string comparison in `User.isAdmin/isCustomer/isSeller`.

`User` also carries three `Boolean` fields (`customer`, `seller`, `admin`) annotated
`@JsonAlias` "for backward compatibility with legacy Redis cache format"
(`domain/model/User.java:47-55`) — nothing in the codebase ever sets or reads them; they
exist purely so old cached JSON blobs (if any still exist in Redis) deserialize without
error.

## Persistence

- **Schema source**: `spring.jpa.hibernate.ddl-auto: update` in every profile
  (`application.yml`, `application-local.yml`) except the test profile, which uses
  `create-drop` (`application-test.yml`). No Flyway/Liquibase — Hibernate owns the schema.
- **Tables**: `"user"` (quoted, reserved word), `Account`, `Address`, `roles` — inconsistent
  casing/quoting across the four tables (`user_service/dataAccess/entity/*.java`).
- **Repositories** (`dataAccess/repository`):
  - `UserRepository`: `findByUsername`, `findByEmail`, `existsByUsername`, `existsByEmail`,
    and `findByUsernameWithAccount` (JPQL `left join fetch u.account`, used by
    login/changePassword to avoid N+1 on the account).
  - `AccountRepository`: `findByUserId`, and `findByUserIdIn` (JPQL `join fetch a.user`,
    used by `UserServiceImpl.getAll()` to batch-load accounts for the user list — still
    N+1 relative to per-user calls but batches all accounts in one query).
  - `AddressRepository`, `RoleRepository`: no custom finders beyond `RoleRepository.findByName`/`existsByName` (the latter is declared but never called anywhere — dead code).
- `AddressServiceImpl.getAll()` and `.search()` load the **entire** address table into
  memory and filter in Java (`domain/service/impl/AddressServiceImpl.java:120-143`) —
  no DB-level filtering; same pattern in `UserServiceImpl.search()` (delegates to
  `getAll()` then filters).

## HTTP API

Base paths: `UserController` → `/api/v1/user` (via `ApiPaths.BASE_PATH`);
`AddressController` → `/api/v1/address` (hardcoded); `RoleController` → `/api/v1/roles`
(hardcoded).

Authorization column describes what *this service* enforces. All three controllers
assume the API gateway has already authenticated the caller; none of them re-validate
a JWT locally (no Spring Security filter chain — `spring-security` is not a dependency,
only `spring-security-crypto` for Argon2).

### UserController (`/api/v1/user`)

| Method | Path | Request | Response | Status | Authorization |
|---|---|---|---|---|---|
| GET | `` | — | `List<UserDto>` | 200 | None — any caller lists all users. |
| GET | `/{userId}` | path `userId: UUID` | `UserDto` or empty body | 200 / 404 | None. |
| GET | `?username=` | query `username` | `UserDto` or empty body | 200 / 404 | None. |
| GET | `/search?q=` | query `q` (optional) | `List<UserDto>` | 200 | None. |
| POST | `/register` | body `UserRegisterDto` (**not** `@Valid`) | `UserDto` | 201 | None — public registration. |
| POST | `/login` | body `UserLoginDto` | JWT string (`Content-Type` not JSON, `String` body) | 202 on success / throws `NoSuchElementException` → 404 on bad credentials | None (public). |
| PUT | `/{userId}` | body `UserUpdateRequest` (**not** `@Valid`), header `X-User-ID: UUID` (required) | `UserDto` or empty body | 200 / 404 | Manual check in `UserServiceImpl.update`: `X-User-ID` must equal path `{userId}` or throws `NoSuchElementException` (→ 404, not 403). |
| DELETE | `/{userId}` | header `X-User-ID: UUID` (required) | — | 204 | Same manual self-only check in `UserServiceImpl.delete`. |

Note: `login` returns HTTP 202 Accepted for a successful login, not 200 — unusual choice,
kept as-is since it is deliberate code, not a typo of e.g. 200/201.

### AddressController (`/api/v1/address`)

| Method | Path | Request | Response | Status | Authorization |
|---|---|---|---|---|---|
| GET | `` | — | `List<CreateAddressResponse>` | 200 | None — lists every address for every user. |
| GET | `/{addressId}` | path `addressId` | `CreateAddressResponse` or empty | 200 / 404 | None. |
| GET | `/search` | query `userId` (optional), `q` (optional) | `List<CreateAddressResponse>` | 200 | None. |
| POST | `` | body `CreateAddressRequest` (`@Valid`), header `X-User-ID: UUID` (required) | `CreateAddressResponse` | 201 | Address is created under the `X-User-ID` supplied by caller — no check that this equals any authenticated identity beyond gateway trust. |
| PUT | `/{addressId}` | body `AddressUpdateRequest` (`@Valid`), header `X-User-ID` (required) | `CreateAddressResponse` or empty | 200 / 404 | Manual check in `AddressServiceImpl.update`: address's owning user must equal `X-User-ID`, else `NoSuchElementException` (→404). |
| DELETE | `/{addressId}` | header `X-User-ID` (required) | — | **200** (`ResponseEntity.ok().build()`, not 204) | Same manual ownership check in `AddressServiceImpl.delete`. |

### RoleController (`/api/v1/roles`)

| Method | Path | Request | Response | Status | Authorization |
|---|---|---|---|---|---|
| GET | `` | — | `List<RoleResponse>` | 200 | None. |
| GET | `/{id}` | path `id` | `RoleResponse` (NPE risk if role missing — see Gotchas) | 200 | None. |
| POST | `` | body `RoleRequest` (`@Valid`, only `name` is `@NotBlank`) | `RoleResponse` | 201 | **None at all** — any caller (no `X-User-ID`, no role check) can create roles. |
| DELETE | `/{id}` | path `id` | — | **200** (not 204) | **None at all** — any caller can delete any role, including ones in use by users (no FK-safety check before delete). |

## Outbound dependencies

None found. `user-service` has `spring-cloud-starter-openfeign` and
`spring-cloud-starter-loadbalancer` on the classpath (`pom.xml`) but no `@FeignClient`
interfaces exist anywhere under `src/main` — these dependencies appear to be
carried but unused in this module (or reserved for a future call-out, e.g. to
notify another service on registration). No `WebClient`/`RestTemplate` usage either.

## Configuration

Profiles: `application.yml` (default/docker), `application-local.yml`, `application-ide.yml`
(activated via `spring.profiles.active`, not shown as explicitly set anywhere in this
module — presumably selected by `SPRING_PROFILES_ACTIVE` env var at deploy time), and
test-only `application-test.yml`. The module previously had a `bootstrap.properties`
configuring Spring Cloud Config/Eureka; it was removed (issue #50) since it was inert
(every YAML profile disables config/discovery) and this project uses Kubernetes DNS,
not Eureka.

| Property | Default | Source / override |
|---|---|---|
| `server.port` | `8080` (`application.yml`), `8081` (`application-ide.yml`), `0` random (`application-test.yml`) | The Dockerfile comment ("Port is dynamically assigned (server.port=0 in application.properties)", `Dockerfile:34`) is **stale** — the shipped `application.yml` hardcodes port `8080`, not `0`. Only the test profile uses `0`. |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/userdb` | env `SPRING_DATASOURCE_URL` (only in `application.yml`; `application-local.yml`/`application-ide.yml` hardcode values, no env override). |
| `spring.datasource.username`/`password` | `postgres`/*(none — required)* | env `SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` (default profile only) — `password` has no default, a missing env var fails startup rather than falling back to a committed value. |
| `spring.jpa.hibernate.ddl-auto` | `update` (prod-ish profiles), `create-drop` (test) | — |
| `spring.data.redis.host`/`port` | `localhost:6379` (default), `redis:6379` (`application-local.yml`) | env `SPRING_DATA_REDIS_HOST`/`SPRING_DATA_REDIS_PORT` (default profile only). |
| `management.zipkin.tracing.endpoint` | `http://zipkin:9411/api/v2/spans` | env `ZIPKIN_BASE_URL` (default/local); hardcoded `http://localhost:9411/...` in `application-ide.yml`. |
| `management.tracing.sampling.probability` | `1.0` | 100% trace sampling — fine for dev, would be expensive in real prod. |
| `GATEWAY_URL` | `http://localhost:8765` | Only set in `application-ide.yml`; unused elsewhere in this module (no outbound HTTP calls reference it — see Outbound dependencies). |

JWT config is **not externalized** — see Security.

## Caching

Redis-backed, `RedisCacheManager` configured in `infrastructure/cache/CacheConfig.java`:
flat **10-minute TTL** (`Duration.ofMinutes(10)`) for every cache name, no per-cache
override, `disableCachingNullValues()`. Values serialized with
`GenericJackson2JsonRedisSerializer` using an `ObjectMapper` that has
`activateDefaultTyping(..., ObjectMapper.DefaultTyping.NON_FINAL, ...)` enabled with
`allowIfBaseType(Object.class)` — see Gotchas for the deserialization-gadget risk this
creates.

| Cache name (`CacheConstants`) | Populated by | Key | Evicted by |
|---|---|---|---|
| `usersById` | `UserServiceImpl.find(id)` | `#id` | `UserServiceImpl.delete` (`@CacheEvict(..., key = "#id")`) |
| `usersByEmail` | **Never** — no method is annotated `@Cacheable(value = CacheConstants.USERS_BY_EMAIL, ...)` | — | `UserServiceImpl.delete` attempts `@CacheEvict(value = CacheConstants.USERS_BY_EMAIL, key = "#username")` — see Gotchas, this is broken. |
| `userByUsername` | `UserServiceImpl.findByUsername(username)` | `#username` | Never evicted (not even on `update`/`delete` for that user — stale for up to 10 min after a profile update). |
| `addressById` | `AddressServiceImpl.find(id)` | `#id` | `AddressServiceImpl.delete` and `.update` (both `@CacheEvict(..., key = "#id")`) |

`UserServiceImpl.update()` and `.changePassword()` do not evict `usersById` or
`userByUsername` — a cached `find(id)`/`findByUsername` result can serve stale data
for up to 10 minutes after a profile edit or password change.

## Security

- **Password hashing**: Argon2id via Spring Security Crypto, configured in
  `infrastructure/security/PasswordConfig.java`:
  `new Argon2PasswordEncoder(16, 32, 1, 60000, 3)` → 16-byte salt, 32-byte hash,
  parallelism 1, memory 60000 KB (~59 MB), 3 iterations. Wrapped behind the
  `PasswordHasher` interface, implemented by `Argon2PasswordHasher`.
- **JWT** (`infrastructure/security/JwtService.java`):
  - Algorithm: `HS256` (`SignatureAlgorithm.HS256`, `io.jsonwebtoken` / jjwt 0.11.5).
  - Secret: injected via `@Value("${jwt.secret}")` into `JwtService`'s constructor, backed
    by `application.yml`'s `jwt.secret: ${JWT_SECRET}` — no committed default; a missing
    `JWT_SECRET` env var fails application startup instead of falling back to a known value.
    Must match `api-gateway`'s `jwt.secret`/`JWT_SECRET` (same signing key, HS256) — still
    two independently-set values rather than shared config, so they can drift if configured
    inconsistently per service, just no longer via a copy-pasted source literal.
  - Expiry: `JwtConstants.EXPIRATION_TIME = 30 minutes`, still hardcoded, no per-profile override.
  - Claims: subject = username only; no roles/authorities, no issuer, no audience claim.
  - `generateToken`/`createToken`/`getSignKey` are instance methods on the `@Component`
    (constructor-injected into `UserServiceImpl` rather than called statically); this and
    `extractUsername`/`extractExpiration`/`extractClaim`/`extractAllClaims` are now all
    instance methods on the same class, and the extraction methods are **still never called
    anywhere in this module** (dead code here; token validation happens in `api-gateway`'s
    own, separate JWT filter, not by calling into this class).
- **Authorization model**: this service performs no independent authentication. It
  trusts the `X-User-ID` request header verbatim as the caller's identity (set by
  `api-gateway`'s `JwtAuthFilter` after validating the bearer token). `UserController`
  and `AddressController` mutating endpoints declare `@RequestHeader("X-User-ID")`
  without `required = false`, so Spring auto-rejects requests missing the header —
  but see Gotchas for what status code that actually produces here.
  `RoleController` has **no** header/identity check at all on create or delete.
- Ownership enforcement is manual, duplicated per-service (`UserServiceImpl.update/delete`,
  `AddressServiceImpl.update/delete`), each with an identical `// TODO (investigate SpEL)`
  comment proposing to replace it with `@PreAuthorize` once a security context exists at
  the service layer (`domain/service/UserService.java:11-13`,
  `domain/service/AddressService.java:11-13`) — i.e. the current state is acknowledged
  as a stopgap in the code itself.
- Failed ownership checks throw `common`'s `NoSuchElementException`, which
  `GlobalExceptionHandler` maps to **404 Not Found** — not 403 Forbidden. Not
  necessarily wrong (avoids confirming a resource's existence to a non-owner) but
  conflates "not found" and "not yours" under one status/exception type.
- `login()` does not check `Account.canLogin()` (`activated && !archived && emailVerified`)
  — an unactivated, unverified, or archived account can still authenticate successfully
  as long as the password matches. `Account.activate()`/`canLogin()` exist but are never
  invoked anywhere in `src/main`.

## Tests

- `user-service/src/test/java/.../BaseIntegrationTest.java`: Testcontainers scaffold —
  spins up `postgres:16-alpine` and `redis:7-alpine`, wires their connection info into
  Spring properties via `@DynamicPropertySource`, `@ActiveProfiles("test")`.
- `user-service/src/test/java/.../UserServiceImplApplicationTests.java`: the **only**
  test in the module, and it is an empty `contextLoads()` smoke test — no controller,
  service, mapper, or repository test exists for any of the User/Account/Address/Role
  flows described above (registration, login, ownership checks, caching, validation).
- Run: `mvn -pl user-service test` (needs a running Docker daemon for Testcontainers)
  or `mvn -pl user-service -am test` from the repo root.

## Gotchas

1. **Broken cache eviction on user delete** — `UserServiceImpl.java:138-141`:
   `@CacheEvict(value = CacheConstants.USERS_BY_EMAIL, key = "#username")` inside
   `delete(UUID id, UUID requestingUserId)`. There is no `username` parameter on this
   method, so the SpEL key expression `#username` cannot resolve against the method's
   actual arguments (`id`, `requestingUserId`). This will raise a SpEL evaluation error
   when the eviction is processed, which — unless caught upstream — turns every
   successful `DELETE /api/v1/user/{userId}` into a 500 from `GlobalExceptionHandler`'s
   catch-all after the row is already deleted. Compounded by targeting `usersByEmail`,
   a cache that is never populated by anything (`@Cacheable` never targets it) — the
   eviction is pointless even if it resolved. **Severity: critical.**
2. **Hardcoded, source-committed JWT signing secret** — `constants/JwtConstants.java:7`.
   Same key for every environment/profile, not read from an env var or secret manager,
   visible to anyone with repo read access; compromise of the repo means every issued
   token everywhere can be forged. **Severity: critical.**
3. **No `@Valid` on registration/update DTOs** — `application/controller/UserController.java:60,76-78`.
   `POST /register` (`UserRegisterDto`) and `PUT /{userId}` (`UserUpdateRequest`)
   accept request bodies with no `@Valid` annotation, and the domain-service `UserUpdateRequest`
   DTO has no Bean Validation constraints regardless — a caller can register/update a
   user with blank name/email/phone, malformed email, etc. Contrast with
   `AddressController`/`RoleController`, which do use `@Valid` + `@NotBlank`.
   **Severity: high.**
4. **No authorization on RoleController create/delete** — `application/controller/RoleController.java:35-46`.
   Any caller that can reach the service (i.e. anyone who clears the gateway) can create
   arbitrary roles or delete any role by ID, with no `X-User-ID` check, no admin check,
   and no guard against deleting a role still referenced by `UserEntity.role`
   (FK behavior on delete is whatever Postgres/Hibernate defaults to — not addressed
   in code). **Severity: high.**
5. **Ownership trust boundary rests entirely on the `X-User-ID` header** — `UserServiceImpl.update/delete`
   (`domain/service/impl/UserServiceImpl.java:143-156,182-204`),
   `AddressServiceImpl.update/delete` (`domain/service/impl/AddressServiceImpl.java:75-117`).
   This service does not itself verify a JWT or any credential; it fully trusts that
   `api-gateway` set `X-User-ID` correctly and that no path to this service bypasses the
   gateway. There is no defense-in-depth if that assumption is ever violated (e.g. a
   misconfigured route, a compromised gateway, or direct pod-to-pod access inside the
   cluster). **Severity: high** (design-level, not a code bug per se).
6. **`login()` ignores account activation/verification state** — `domain/service/impl/UserServiceImpl.java:158-180`.
   `Account.canLogin()` (`activated && !archived && emailVerified`) is defined but never
   called; a correct password alone is sufficient to receive a token regardless of
   account status. **Severity: medium.**
7. **Insecure Jackson default typing in Redis cache serializer** — `infrastructure/cache/CacheConfig.java:34-40`.
   `activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(), NON_FINAL, PROPERTY)`
   embeds `@class` type info in every cached JSON value and allows deserialization of
   any subtype of `Object`. If an attacker can write to the Redis instance this service
   reads from (e.g. via a separate Redis exposure/misconfig), this is a classic
   polymorphic-deserialization gadget-chain risk. **Severity: medium** (requires a
   secondary Redis-write vector to exploit, but the validator itself provides no
   meaningful restriction).
8. **`AccountMapper` never maps the `archived` field** — `dataAccess/mapper/AccountMapper.java`.
   `Account.archived`/`AccountEntity.archived` exist on both sides but neither
   `toEntity` nor `toDomain` sets it — `archived` is silently always `false` after any
   round-trip through this mapper, even if the DB row has `archived = true`.
   **Severity: medium.**
9. **Stale caches on profile update/password change** — `UserServiceImpl.update()`
   (`domain/service/impl/UserServiceImpl.java:182-204`) and `.changePassword()`
   (`:221-254`) don't evict `usersById` or `userByUsername`. A `find`/`findByUsername`
   call within 10 minutes of an update can return the pre-update `UserResponse`, and
   after a password change a still-cached (id-keyed) lookup elsewhere in the request
   chain wouldn't itself leak the password (responses never include it), but the
   overall cache/DB divergence window is a correctness gap. **Severity: medium.**
10. **Inconsistent DELETE status codes** — `UserController.delete` returns 204
    (`application/controller/UserController.java:87-92`), but
    `AddressController.delete` and `RoleController.delete` both return 200 with an
    empty body (`AddressController.java:73-78`, `RoleController.java:42-46`).
    **Severity: low.**
11. **Missing `X-User-ID` header likely surfaces as 500, not 400** — `UserController.java:76-78,88-89`,
    `AddressController.java:53-54,60-63,73-76`. The header is a required
    `@RequestHeader`; Spring throws `MissingRequestHeaderException` when it's absent.
    `GlobalExceptionHandler` has no specific handler for that type, so it falls through
    to the catch-all `@ExceptionHandler(Exception.class)`, which always returns
    **500 Internal Server Error** with the message "An unexpected error occurred" —
    masking what is really a 400-class client error. **Severity: medium.**
12. **Dead code: `EditAddressRequest` / `EditAddressResponse`** —
    `application/dto/EditAddressRequest.java`, `EditAddressResponse.java`. Both are
    empty classes with no fields, never referenced by any controller, mapper, or test
    (`AddressController` uses `AddressUpdateRequest`/`CreateAddressResponse` instead).
    **Severity: low.**
13. **Dead code: `exception.MethodArgumentNotValidException`** —
    `exception/MethodArgumentNotValidException.java`. A custom exception class that
    shadows Spring's own `org.springframework.web.bind.MethodArgumentNotValidException`
    by name; `GlobalExceptionHandler` imports and handles the *Spring* class, not this
    one, so this custom class is never thrown or caught anywhere. **Severity: low.**
14. **Dead code: `domain.enums.UserRole`** — `domain/enums/UserRole.java`. Declared,
    never referenced; actual role checks (`User.isAdmin/isCustomer/isSeller`) compare
    `role.getName()` strings against `"ADMIN"`/`"SELLER"`/`"CUSTOMER"` literals instead.
    **Severity: low.**
15. **Dead/unused `JwtService` instance methods** — `infrastructure/security/JwtService.java`.
    `extractUsername`, `extractExpiration`, `extractClaim`, `extractAllClaims` are never
    called anywhere in `user-service`; only `generateToken` is used (constructor-injected
    into `UserServiceImpl`, called from `.login`). Token *validation* happens exclusively in
    `api-gateway`'s own filter, which does not call into this class (separate module,
    separate JWT handling code). **Severity: low.**
16. **Unused Feign/LoadBalancer dependencies** — `pom.xml` declares
    `spring-cloud-starter-openfeign`, `feign-micrometer`, and
    `spring-cloud-starter-loadbalancer`, but no `@FeignClient` or outbound HTTP client
    exists anywhere in `src/main`. Either vestigial or reserved for unbuilt
    functionality. **Severity: low.**
17. **Stale Dockerfile comment** — `Dockerfile:34`: `# Port is dynamically assigned
    (server.port=0 in application.properties)`. The shipped `application.yml` sets
    `server.port: 8080`, not `0`; only the test profile (`application-test.yml`) uses
    `0`. **Severity: low.**
18. ~~`bootstrap.properties` looks vestigial~~ — removed (issue #50). It configured
    `spring.cloud.config.discovery.enabled=true` and an Eureka `defaultZone`, but every
    YAML profile explicitly set `spring.cloud.config.enabled: false` and
    `spring.cloud.config.discovery.enabled: false`, so it did nothing observable.
    **Severity: low.**
19. **`AddressEntity.DefaultAddress` field naming** — `dataAccess/entity/AddressEntity.java:46`.
    Field is `private boolean DefaultAddress` (capitalized), producing a non-conventional
    Lombok builder method `.DefaultAddress(...)` (used at `dataAccess/mapper/AddressMapper.java:13`)
    while the getter (`isDefaultAddress`) and every other layer use lowercase
    `defaultAddress`. Cosmetic but repo convention calls out avoiding casual renames —
    flagging rather than fixing. **Severity: low.**
20. **`RoleRepository.existsByName` and `Account.activate()`/`canLogin()` are unused** —
    declared, never called from any service. Minor dead surface. **Severity: low.**
