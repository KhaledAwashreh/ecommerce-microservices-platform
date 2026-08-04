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
│   ├── enums/                           AccountStatus, AccountType, Gender, UserRole (used by RoleController.isAdmin — see below)
│   ├── model/                           User, Account, Address, Role (plain POJOs, business methods)
│   └── service/ + service.impl/         UserService, AddressService, RoleService + impls; service/dto holds the internal request/response shapes
├── exception/
│   └── GlobalExceptionHandler.java      @RestControllerAdvice
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

`domain/enums/UserRole` (`ADMIN`/`SELLER`/`CUSTOMER`) is used by
`RoleController.isAdmin` (`UserRole.ADMIN.name()`, added for the GH #18 admin check — see
the amendment note at the top of this doc) but nowhere else; `User.isAdmin/isCustomer/isSeller`
still resolves role membership via `Role.name` string comparison, not this enum. (An
earlier version of this doc called `UserRole` entirely dead code and issue #51 repeated
that claim — both are now stale; verify current usage before removing it.)

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
| POST | `/register` | body `UserRegisterDto` (`@Valid`: `@NotBlank` name/username/phone/rawPassword, `@NotBlank @Email` email, `@NotNull` birthdate — GH #40) | `UserDto` | 201, 400 on validation failure | None — public registration. |
| POST | `/login` | body `UserLoginDto` | JWT string (`Content-Type` not JSON, `String` body) | 202 on success / throws `UnauthorizedException` → **401** on bad credentials (fixed for GH #37; was `NoSuchElementException` → 404) | None (public). |
| PUT | `/{userId}` | body `UserUpdateRequest` (`@Valid`, but only `email` carries a constraint - `@Email`, format-only since a `null` email means "leave unchanged" per `UserServiceImpl.update`'s partial-update semantics — GH #40), header `X-User-ID: UUID` (required) | `UserDto` or empty body | 200 / 400 (missing header GH #44, or malformed email GH #40) / 404 (user truly doesn't exist) / **403** (fixed for GH #37; was 404) | Manual check in `UserServiceImpl.update`: `X-User-ID` must equal path `{userId}` or throws `ForbiddenException` (→ 403). |
| DELETE | `/{userId}` | header `X-User-ID: UUID` (required) | — | 204 / **403** (fixed for GH #37; was 404) | Same manual self-only check in `UserServiceImpl.delete`, now throws `ForbiddenException`. |

Note: `login` returns HTTP 202 Accepted for a successful login, not 200 — unusual choice,
kept as-is since it is deliberate code, not a typo of e.g. 200/201.

### AddressController (`/api/v1/address`)

| Method | Path | Request | Response | Status | Authorization |
|---|---|---|---|---|---|
| GET | `` | header `X-User-ID: UUID` (required) | `List<CreateAddressResponse>` | 200 / 400 (missing header) | GH #64 fix: scoped to the caller's own addresses via `X-User-ID`, same pattern as `/search` (GH #59). Previously took no argument and returned every address for every user. |
| GET | `/{addressId}` | path `addressId` | `CreateAddressResponse` or empty | 200 / 404 | None. |
| GET | `/search` | header `X-User-ID: UUID` (required), query `q` (optional) | `List<CreateAddressResponse>` | 200 / 400 (missing header) | GH #59 fix: scoped to the caller's own addresses via `X-User-ID`; the endpoint no longer accepts a caller-supplied `userId` query param. |
| POST | `` | body `CreateAddressRequest` (`@Valid`), header `X-User-ID: UUID` (required) | `CreateAddressResponse` | 201 | Address is created under the `X-User-ID` supplied by caller — no check that this equals any authenticated identity beyond gateway trust. |
| PUT | `/{addressId}` | body `AddressUpdateRequest` (`@Valid`), header `X-User-ID` (required) | `CreateAddressResponse` or empty | 200 / 404 (address truly doesn't exist) / **403** (fixed for GH #37; was 404) | Manual check in `AddressServiceImpl.update`: address's owning user must equal `X-User-ID`, else `ForbiddenException` (→403). |
| DELETE | `/{addressId}` | header `X-User-ID` (required) | — | **200** (`ResponseEntity.ok().build()`, not 204) / **403** (fixed for GH #37; was 404) | Same manual ownership check in `AddressServiceImpl.delete`, now throws `ForbiddenException`. |

### RoleController (`/api/v1/roles`)

| Method | Path | Request | Response | Status | Authorization |
|---|---|---|---|---|---|
| GET | `` | — | `List<RoleResponse>` | 200 | None. |
| GET | `/{id}` | path `id` | `RoleResponse` (NPE risk if role missing — see Gotchas) | 200 | None. |
| POST | `` | body `RoleRequest` (`@Valid`, only `name` is `@NotBlank`) | `RoleResponse` | 201 | **None at all** — any caller (no `X-User-ID`, no role check) can create roles. |
| DELETE | `/{id}` | path `id` | — | **200** (not 204) | **None at all** — any caller can delete any role, including ones in use by users (no FK-safety check before delete). |

## Outbound dependencies

None. `user-service` no longer carries Feign/load-balancer dependencies —
`spring-cloud-starter-openfeign`, `feign-micrometer`, and `spring-cloud-starter-loadbalancer`
were removed from `pom.xml` (issue #51): no `@FeignClient` interface ever existed anywhere
under `src/main`. No `WebClient`/`RestTemplate` usage either. Note: one of the removed
dependencies (`spring-cloud-starter-loadbalancer` → `spring-cloud-context`) was transitively
supplying `org.bouncycastle:bcprov-jdk18on`, which `Argon2PasswordEncoder` (see Security)
needs on the classpath at runtime — removing it without adding `bcprov-jdk18on` directly
broke password hashing (`NoClassDefFoundError` on `Argon2Parameters$Builder`). `pom.xml` now
declares `bcprov-jdk18on` explicitly instead of relying on that transitive path.

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
| `server.port` | `8080` (`application.yml`), `8081` (`application-ide.yml`), `0` random (`application-test.yml`) | The Dockerfile comment (`Dockerfile:34`) was stale — fixed (GH #52) to say the port is fixed at `8080`; only the test profile uses `0`. |
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
- **(Fixed for GH #37)** Failed ownership checks now throw `common`'s new
  `ForbiddenException`, which `GlobalExceptionHandler` maps to **403 Forbidden** — not
  404 Not Found. Previously both this case and a genuine "resource doesn't exist" case
  threw `common`'s `NoSuchElementException` (→ 404), making the two indistinguishable
  from the caller's side and from the code (an accident, not a deliberate "don't confirm
  existence to a non-owner" design choice — nothing in the code or docs said otherwise).
  A real "not found" (e.g. `GET /{userId}` for a nonexistent user, or
  `AddressServiceImpl.create` for a nonexistent user) still throws/returns 404 as before;
  only the ownership-mismatch branches changed. Login failure is a third, separate case:
  `UserController.login` now throws `common`'s new `UnauthorizedException` (→ **401**),
  not `NoSuchElementException`/404 — a wrong password was never a "not found" or
  "forbidden" condition. See `exception/GlobalExceptionHandlerTest.java` for the status
  mapping and `UserServiceImplIntegrationTest`/`AddressServiceImplIntegrationTest`/
  `UserControllerTest` for the three call sites.
- **(Fixed for GH #32/#33)** `AccountMapper` now maps `archived` in both directions, and
  `login()` (`domain/service/impl/UserServiceImpl.java`) now rejects an archived account
  after a correct password check, returning `null` (→ 401 "Invalid username or password"
  via `UserController.login`), same as a wrong password. This is intentionally scoped to
  `archived` only, not the full `Account.canLogin()` predicate
  (`activated && !archived && emailVerified`): nothing in this codebase ever sets
  `activated`/`emailVerified` true (there is no activation or email-verification flow,
  and `Account.activate()` still has zero callers), so enforcing the full predicate would
  reject every account, including ones freshly registered — a far larger behavior change
  than either issue asked for. `Account.canLogin()` remains defined but effectively only
  half-wired; fully honoring it is a follow-up that needs a registration/verification flow
  decision, not something this fix invented on its own.

## Tests

- `user-service/src/test/java/.../BaseIntegrationTest.java`: Testcontainers scaffold —
  spins up `postgres:16-alpine` and `redis:7-alpine`, wires their connection info into
  Spring properties via `@DynamicPropertySource`, `@ActiveProfiles("test")`. Has a real
  subclass, `UserServiceImplIntegrationTest` (service-layer, `delete`/ownership/cache
  eviction coverage) — this note is stale where it implies `contextLoads()` is the only
  test; it predates that addition.
- `user-service/src/test/java/.../UserServiceImplApplicationTests.java`: empty
  `contextLoads()` smoke test.
- `user-service/src/test/java/.../application/controller/UserControllerTest.java`: a
  `@WebMvcTest` slice (JwtAuthFilter excluded from component scanning, same pattern as
  the web-layer slice tests in other modules) covering the GH #44 fix (missing
  `X-User-ID` header on `PUT /{userId}` → 400, not 500) and the GH #40 fix (`/register`
  and `PUT /{userId}` reject invalid payloads via `@Valid` before reaching
  `UserService`). Plain Spring MVC test context, no Testcontainers/Docker needed.
- `user-service/src/test/java/.../application/controller/UserControllerLoginTest.java`: a
  separate, plain-Mockito unit test (not a `@WebMvcTest` slice) covering the GH #37 fix —
  `login()` throwing `UnauthorizedException` on bad credentials.
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
3. ~~**No `@Valid` on registration/update DTOs**~~ — fixed (GH #40). `POST /register`
   (`UserRegisterDto`) and `PUT /{userId}` (`UserUpdateRequest`) now validate with
   `@Valid`; see the HTTP API table above for exactly what's constrained. Note
   `UserUpdateRequest` only got a format constraint on `email` (`@Email`, which allows
   `null`) — it's a genuine partial update (`UserServiceImpl.update` treats a `null`
   field as "leave unchanged"), so `@NotBlank`/`@NotNull` on its other fields would have
   broken legitimate partial updates.
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
6. **(Fixed for GH #33) `login()` now rejects archived accounts.** `Account.canLogin()`
   (`activated && !archived && emailVerified`) is still defined but only its `archived`
   half is enforced in `login()` — see the Security section note above for why the
   `activated`/`emailVerified` portion is deliberately not (yet) enforced. **Severity now:
   low** (remaining gap is `activated`/`emailVerified` not being enforceable without a
   registration/verification flow that doesn't exist yet).
7. **Insecure Jackson default typing in Redis cache serializer** — `infrastructure/cache/CacheConfig.java:34-40`.
   `activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(), NON_FINAL, PROPERTY)`
   embeds `@class` type info in every cached JSON value and allows deserialization of
   any subtype of `Object`. If an attacker can write to the Redis instance this service
   reads from (e.g. via a separate Redis exposure/misconfig), this is a classic
   polymorphic-deserialization gadget-chain risk. **Severity: medium** (requires a
   secondary Redis-write vector to exploit, but the validator itself provides no
   meaningful restriction).
8. **(Fixed for GH #32) `AccountMapper` now maps the `archived` field** —
   `dataAccess/mapper/AccountMapper.java`. Previously neither `toEntity` nor `toDomain`
   set it, so `archived` was silently always `false` after any round-trip through this
   mapper even if the DB row had `archived = true`; this is what let `login()`'s new
   archived check (GH #33) actually work end-to-end.
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
11. ~~**Missing `X-User-ID` header likely surfaces as 500, not 400**~~ — fixed (GH #44).
    `GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`) now has explicit
    handlers for `MissingRequestHeaderException` and
    `MissingServletRequestParameterException` ahead of the catch-all
    `@ExceptionHandler(Exception.class)`, both returning 400 with `common.dto.ErrorResponse`.
12. ~~Dead code: `EditAddressRequest` / `EditAddressResponse`~~ — removed (issue #51).
    Both were empty classes with no fields, never referenced by any controller, mapper,
    or test (`AddressController` uses `AddressUpdateRequest`/`CreateAddressResponse`
    instead).
13. ~~Dead code: `exception.MethodArgumentNotValidException`~~ — removed (issue #51). A
    custom exception class that shadowed Spring's own
    `org.springframework.web.bind.MethodArgumentNotValidException` by name;
    `GlobalExceptionHandler` imports and handles the *Spring* class, not this one, so
    this custom class was never thrown or caught anywhere.
14. **`domain.enums.UserRole` is not dead** — `domain/enums/UserRole.java` is used by
    `RoleController.isAdmin` (`UserRole.ADMIN.name()`). See the note in Domain model
    above; a prior version of this doc and issue #51 both called it dead code, but that's
    stale as of the GH #18 fix. `User.isAdmin/isCustomer/isSeller` still compares
    `role.getName()` strings against `"ADMIN"`/`"SELLER"`/`"CUSTOMER"` literals instead of
    this enum, so the two role-check mechanisms remain inconsistent with each other.
15. **Dead/unused `JwtService` instance methods** — `infrastructure/security/JwtService.java`.
    `extractUsername`, `extractExpiration`, `extractClaim`, `extractAllClaims` are never
    called anywhere in `user-service`; only `generateToken` is used (constructor-injected
    into `UserServiceImpl`, called from `.login`). Token *validation* happens exclusively in
    `api-gateway`'s own filter, which does not call into this class (separate module,
    separate JWT handling code). **Severity: low.**
16. ~~Unused Feign/LoadBalancer dependencies~~ — removed (issue #51). `pom.xml` declared
    `spring-cloud-starter-openfeign`, `feign-micrometer`, and
    `spring-cloud-starter-loadbalancer`, but no `@FeignClient` or outbound HTTP client
    existed anywhere in `src/main`. `spring-cloud-starter-loadbalancer` was transitively
    supplying `bcprov-jdk18on` (BouncyCastle) that `Argon2PasswordEncoder` needs at
    runtime — `pom.xml` now declares that dependency directly instead (see Outbound
    dependencies).
17. ~~**Stale Dockerfile comment**~~ **Fixed (GH #52).** `Dockerfile:34` used to read
    `# Port is dynamically assigned (server.port=0 in application.properties)`. The shipped
    `application.yml` sets `server.port: 8080`, not `0`; only the test profile
    (`application-test.yml`) uses `0`. The comment now says so.
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
