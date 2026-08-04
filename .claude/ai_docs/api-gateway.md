# api-gateway

> **Amendment (GH #52 fix):** the route tables, resilience4j config, and several Gotchas
> below (1/3/7/8/9/10) predate the GH #52 config-drift fix and are now stale in the specific
> ways noted inline: the `httpbin` route is gone, `application.yml` and
> `application-local.yml` no longer disagree on circuit-breaker/timelimiter numbers or route
> ids, `order-service` is now an explicit circuit-breaker instance in both profiles, and both
> profiles now carry the `frontend-service` catch-all route. The rest of this document is
> otherwise still accurate; it has not been fully regenerated.

> **Amendment (GH #19 fix):** this doc predates the fix and the statements that role
> data is "read from user-service but never turned into a `GrantedAuthority`" and that
> "no `X-User-Role` (or similar) header is propagated downstream" are now **stale**.
> `JwtAuthFilter` now reads the `role` claim directly off the token (embedded at
> issuance by user-service, since the `UserDto` this filter's `userServiceClient`
> fetches still does not serialize role), builds a `SimpleGrantedAuthority("ROLE_" +
> role)` for the reactive security context, and adds an `X-User-Role` header alongside
> the existing `X-User-Name`/`X-User-ID`. Each backend service now independently
> re-validates the JWT and derives its own verified identity/role rather than trusting
> these headers outright (GH #17/#19), so this is now defense-in-depth rather than the
> sole trust boundary. The rest of this document is otherwise still accurate as of the
> fix; it has not been fully regenerated.

## Purpose

Spring Cloud Gateway (WebFlux, reactive) fronting the platform's backend services. It is the
single ingress clients are expected to call (`CLAUDE.md`), routing `/api/v1/**` traffic to
`user-service`, `product-service`, `order-service`, and `payment-service` by path predicate,
applying per-route retry and circuit-breaker filters, authenticating requests with a JWT filter
that calls back into `user-service` to re-fetch the user before minting a Spring Security
`Authentication`, and propagating `X-User-Name` / `X-User-ID` headers downstream. Also exposes
Actuator health/info/metrics, Redis-backed cache infrastructure, and Zipkin tracing export.

## Package layout

```
com.kawashreh.ecommerce.api_gateway/
├── ApiGatewayApplication.java              @SpringBootApplication (entry point)
├── FallbackController.java                 single GET/any-method handler for /fallback
├── Infrastructure/                          (capital "I" — deviates from the lower-case
│                                              infrastructure/ convention in CLAUDE.md)
│   ├── cache/CacheConfig.java               RedisCacheManager, RedisTemplate<String,Object>,
│   │                                          StringRedisTemplate beans; @EnableCaching
│   ├── configuration/
│   │   ├── Resilience4jConfiguration.java   default Resilience4j circuit-breaker Customizer
│   │   ├── SecurityConfig.java              WebFlux SecurityWebFilterChain, public path list
│   │   └── WebClientConfig.java             one WebClient.Builder bean (a second, unused @LoadBalanced one was removed — issue #51)
│   ├── filter/
│   │   ├── JwtAuthFilter.java                WebFilter: JWT auth + header propagation
│   │   └── LoggingFilter.java                GlobalFilter: logs request path only
│   ├── http/
│   │   ├── client/ReactiveUserServiceClient.java  WebClient calls to user-service
│   │   └── dto/UserDto.java                  name, id, username, Role (capitalised field)
│   └── security/JwtService.java              jjwt token parsing/validation (HS256)
└── constants/
    ├── ApiPaths.java                         FALLBACK, USER_BASE, USER_BY_ID only
    └── JwtConstants.java                     EXPIRATION_TIME (30 min); SECRET removed — see jwt.secret below
```

No `domain/`, `dataAccess/`, or `application/` packages — this module has no persistence and
no domain model of its own; it is routing + auth + config only. `ApiPaths` is far from
comprehensive: only 3 constants exist even though 8+ route predicates are defined in YAML, and
none of the YAML route predicates reference `ApiPaths` at all (they're inline path strings in
`application*.yml`).

## Domain model

None. `UserDto` (`Infrastructure/http/dto/UserDto.java`) is the only POJO — a transport DTO
for user-service responses, not a domain entity: `name`, `id` (UUID), `username`, `Role`
(String, capitalised field name — Lombok `@Data` generates `getRole()`/`setRole()`, but the JSON
property key Jackson binds to is `Role`, not `role`; whether this matches user-service's actual
response field is not verified here since that's a different module).

## Persistence

None. No JPA, no datasource, no `ddl-auto`. Redis is present only as a cache backend (see
Caching) and is not used for domain data.

## HTTP API

The gateway itself exposes one real endpoint plus Actuator; everything else is routing.

| Method | Path | Handler | Response | Auth |
|---|---|---|---|---|
| ANY | `/fallback` | `FallbackController.fallback()` | `503` + plain-text body `"Service is currently unavailable. Please try again later."` | Public per `SecurityConfig`? No — not in the `permitAll` list, so `anyExchange().authenticated()` applies. Reachable only via internal `forward:/fallback` from circuit-breaker filters, which bypasses external auth since it's a server-side forward, not a new client request. |
| GET | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics` | Spring Boot Actuator | Actuator JSON | Public (`SecurityConfig` + `JwtAuthFilter.isPublicPath`) |

`ApiGatewayApplicationTests.java` and `pom.xml`'s `spring-boot-starter-actuator` (declared
**twice**, `pom.xml:39` and `pom.xml:159`) back the actuator exposure; `application-local.yml`
additionally sets `management.endpoints.web.exposure.include: health,info,metrics,prometheus`
and turns on liveness/readiness probes — that block is **absent from the default
`application.yml`**.

### Gateway routes

Two different route tables exist — the **default** (`application.yml`, active when no
`SPRING_PROFILES_ACTIVE` is set, e.g. `docker-compose.yaml`) and the **local** profile
(`application-local.yml`, active when `SPRING_PROFILES_ACTIVE=local`, e.g.
`docker-compose.dev.yml` and the `local` folder in k8s). **(GH #52 fix)** They now define the
same route ids/predicates for every backend service, plus the same trailing catch-all — see
below.

**Routes (identical in both `application.yml` and `application-local.yml` as of GH #52):**

| Route id | Predicate path(s) | Target URI (env override) | Filters | Circuit breaker / fallback |
|---|---|---|---|---|
| `user-service` | `Path=/api/v1/user/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `product-service` | `Path=/api/v1/product/**,/api/v1/productReview/**,/api/v1/categories/**,/api/v1/inventory/**,/api/v1/product-variation/**` | `${PRODUCT_SERVICE_URL:http://product-service:8080}` | `Retry` (3, GET/POST) | CB name `product-service` → `forward:/fallback` |
| `order-service` | `Path=/api/v1/orders/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` → `forward:/fallback` |
| `order-cart-service` | `Path=/api/v1/carts/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` (reused, same backend) → `forward:/fallback` — added for GH #13, same "separate route id, shared CB name" pattern as `user-role-service`/`user-address-service` |
| `user-role-service` | `Path=/api/v1/roles/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `user-address-service` | `Path=/api/v1/address/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `payment-service` | `Path=/api/v1/payment/**` | `${PAYMENT_SERVICE_URL:http://payment-service:8080}` | `Retry` (3, GET/POST) | CB name `payment-service` → `forward:/fallback` |
| `frontend-service` | `Path=/**` (catch-all, must stay last) | `http://frontend-service:3000` (hardcoded, no filters) | none | none |

**(GH #52 fix)** Previously: `application.yml` also carried a leftover `httpbin` route to an
external host (`Path=/get` → `http://httpbin.org:80`), a real live route since this is the
profile k8s and `docker-compose.yaml` actually run — removed outright, it had no purpose.
`application-local.yml` merged `/api/v1/user/**` and `/api/v1/address/**` under one
`user-service` route id instead of `application.yml`'s separate `user-service`/
`user-address-service` ids for the same backend — split to match. The `frontend-service`
catch-all previously existed **only** in `application-local.yml`, which is backwards: local
dev never needs it (`docker-compose.dev.yml` maps `frontend-service` directly to host port
`3000`, so browsers never go through the gateway for UI pages), while production
(`application.yml`'s profile) does need it — the k8s Ingress
(`k8s/services/api-gateway/api-gateway-ingress.yaml`) forwards `/` in its entirety to
`api-gateway` with no separate frontend Ingress rule, and the k8s Deployment sets no
`SPRING_PROFILES_ACTIVE`. Added it to `application.yml` too rather than moving it, since it's
harmless in local and this keeps both profiles's route tables identical.

`Retry` and `CircuitBreaker` above are Spring Cloud Gateway's own `GatewayFilterFactory`
implementations (declared via `spring-cloud-starter-gateway` /
`spring-cloud-starter-circuitbreaker-reactor-resilience4j`), configured inline per route with
`args.retries` / `args.methods` and `args.name` / `args.fallbackUri`. The
`resilience4j.retry.*` and `resilience4j.circuitbreaker.*` blocks in the same YAML files
configure the underlying Resilience4j registries these filters resolve against by name (see
Configuration).

## Outbound dependencies

| Client | Type | Target | Used by | Failure handling |
|---|---|---|---|---|
| `ReactiveUserServiceClient` (`Infrastructure/http/client/ReactiveUserServiceClient.java`) | `WebClient` (non-load-balanced `webClientBuilder` bean) | `${USER_SERVICE_URL:http://user-service:8080}` | `JwtAuthFilter`, to re-fetch the user by username on every authenticated request | None explicit — `retrieveByUsername`/`retrieveById` have no `.onErrorResume`/timeout; any error propagates and is caught by `JwtAuthFilter`'s outer `.onErrorResume` which returns 401 |
| Gateway routes (7 backend routes + 1 frontend catch-all) | Spring Cloud Gateway HTTP proxy | user/product/order/payment-service, env-overridable, plus `frontend-service:3000` | end clients | Gateway `Retry` filter (3 attempts, GET/POST) + `CircuitBreaker` filter → `forward:/fallback` (503); the catch-all has neither |
`ReactiveUserServiceClient` is constructed via constructor injection with the module's one
remaining `webClientBuilder` bean. `@EnableFeignClients` (there was never a `@FeignClient`
interface anywhere in this module) and the second, `@LoadBalanced`-qualified
`WebClient.Builder` bean it had no injection point for were both removed as dead code
(issue #51) — see Gotchas.

## Configuration

| Property | File | Default | Notes |
|---|---|---|---|
| `server.port` | `application.yml:107` | `8765` | Fixed in default/local profiles; test profile uses `0` (random) |
| `spring.data.redis.host` / `.port` | `application.yml:6-7` | `${SPRING_DATA_REDIS_HOST:localhost}` / `${SPRING_DATA_REDIS_PORT:6379}` | `application-local.yml` hardcodes host `redis` (no env override); `application-ide.yml` hardcodes `localhost` |
| `USER_SERVICE_URL` | route/client defaults | `http://user-service:8080` | Overridden to `http://localhost:8081` in `application-ide.yml` |
| `PRODUCT_SERVICE_URL` | route defaults | `http://product-service:8080` | `application-ide.yml` → `http://localhost:8082` |
| `ORDER_SERVICE_URL` | route defaults | `http://order-service:8080` | `application-ide.yml` → `http://localhost:8083` |
| `PAYMENT_SERVICE_URL` | route defaults | `http://payment-service:8080` | `application-ide.yml` → `http://localhost:8084` |
| `ZIPKIN_BASE_URL` | `application.yml:112` | `http://zipkin:9411` | Used as `management.zipkin.tracing.endpoint = ${ZIPKIN_BASE_URL}/api/v2/spans` |
| `resilience4j.circuitbreaker.configs.default.*` | `application.yml` | `failureRateThreshold=50`, `slowCallRateThreshold=50`, `waitDurationInOpenState=10000ms`, `slowCallDurationThreshold=2000ms`, `permittedNumberOfCallsInHalfOpenState=3`, `slidingWindowSize=10`, `minimumNumberOfCalls=5` | **(GH #52 fix)** Now numerically identical to `application-local.yml`'s equivalent block and to the programmatic `Resilience4jConfiguration.defaultCustomizer()` bean (pinned by `Resilience4jConfigurationTest`) — previously all three disagreed (see Gotchas) |
| `resilience4j.circuitbreaker.instances.{user-service,product-service,order-service,payment-service}` | `application.yml` | `baseConfig: default` | **(GH #52 fix)** `order-service` is now explicitly listed (it was missing — `user-role-service`/`user-address-service` still reuse the `user-service` CB name by design, not omission) |
| `resilience4j.retry.configs.default.*` | `application.yml` | `maxAttempts=3`, `waitDuration=1000ms`, retry on `IOException`/`ConnectException` | This registry is **not** what backs the per-route `Retry` gateway filter (that filter takes its own inline `args.retries`/`args.methods`); no code in this module invokes Resilience4j's `Retry` API directly, so this block appears to configure an unused registry |
| `resilience4j.timelimiter.*` | `application.yml` and `application-local.yml` | `timeoutDuration=5s`, `cancelRunningFuture=true`, instances for user/product/order/payment-service | **(GH #52 fix)** Previously only in `application-local.yml`; the default profile (what `docker-compose.yaml`/k8s run) had no explicit gateway timeout and fell back to Resilience4j's built-in 1s default |
| `management.zipkin.tracing.endpoint` | all profiles | see above | |
| `logging.level.org.springframework.cloud.gateway` etc. | `application-local.yml:151-153` | `DEBUG` | Only in local profile |
| `jwt.secret` | `application.yml`, `JwtService` constructor (`@Value`) | none — required | Sourced from env var `JWT_SECRET`, no committed default; missing value fails app startup |
| `JwtConstants.EXPIRATION_TIME` | `constants/JwtConstants.java:8` | `1000L * 60 * 30` (30 min) | Still hardcoded — not sourced from env/config |

## Caching

`CacheConfig` (`Infrastructure/cache/CacheConfig.java`) defines a Redis-backed
`RedisCacheManager` (10-minute TTL, null-values disabled, JSON values via
`GenericJackson2JsonRedisSerializer` with `JavaTimeModule`, string keys), a generic
`RedisTemplate<String,Object>`, and a `StringRedisTemplate`, with `@EnableCaching` on the class.
**No cache names, no `@Cacheable`/`@CacheEvict` annotation, and no manual `RedisTemplate` usage
exist anywhere else in this module** (confirmed by grep across `src/main`) — the entire caching
layer is configured but unused dead infrastructure as far as this module's own code is
concerned.

## Security

- **Auth mechanism**: JWT bearer tokens, HS256, validated in `JwtAuthFilter`
  (`Infrastructure/filter/JwtAuthFilter.java`), a `WebFilter` installed at
  `SecurityWebFiltersOrder.AUTHENTICATION` (replacing the default authentication filter) via
  `SecurityConfig.securityWebFilterChain`.
- **Public paths** — defined in **two places that don't fully agree**:
  - `SecurityConfig.securityWebFilterChain` (`Infrastructure/configuration/SecurityConfig.java:27-35`):
    exact `pathMatchers` (no wildcards) for `/api/v1/user/register`, `/api/v1/user/login`,
    `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/actuator/metrics`.
  - `JwtAuthFilter.isPublicPath` (`Infrastructure/filter/JwtAuthFilter.java:79-85`): a
    **substring `path.contains(...)`** check (not exact/prefix match) for the same five
    fragments (`/api/v1/user/register`, `/api/v1/user/login`, `/actuator/health`,
    `/actuator/info`, `/actuator/metrics`).
  - Any other path falls through to `anyExchange().authenticated()` in `SecurityConfig`,
    meaning it is denied unless `JwtAuthFilter` has already populated the reactive security
    context with an `Authentication`.
- **Token flow**: `JwtAuthFilter` reads `Authorization: Bearer <token>`, calls
  `JwtService.extractUsername(token)` (parses/validates signature via
  `Jwts.parserBuilder().setSigningKey(...)`, throws on bad signature/expiry — caught by the
  outer `.onErrorResume` → 401), then calls
  `ReactiveUserServiceClient.retrieveByUsername(username)` against user-service to fetch a
  fresh `UserDto`, then `JwtService.validateToken(token, userDetails)` (checks
  `username.equals(userDetails.getUsername())` and expiry again). On success it builds a
  `UsernamePasswordAuthenticationToken(userDetails, null, null)` (no granted authorities — the
  `UserDto.Role` field is read from user-service but never turned into a `GrantedAuthority`),
  writes it to `ReactiveSecurityContextHolder`, and mutates the outgoing request to add
  **`X-User-Name`** and **`X-User-ID`** headers before forwarding downstream. No role/claims
  header is propagated.
- **Secret management**: `JwtService`'s signing key is injected via `@Value("${jwt.secret}")`
  (constructor param, `Infrastructure/security/JwtService.java`), backed by
  `application.yml`'s `jwt.secret: ${JWT_SECRET}` (no default — a missing `JWT_SECRET` env
  var fails startup). Still duplicated with `user-service`'s equivalent `jwt.secret`/
  `JWT_SECRET` rather than shared config, so the two can still drift if someone sets
  different values per service — just no longer via a copy-pasted source literal.
- **CORS**: no `CorsWebFilter`, `CorsConfigurationSource`, `@CrossOrigin`, or
  `spring.cloud.gateway.globalcors` / `default-filters` CORS config exists anywhere in this
  module's source or YAML (confirmed by grep). CORS is not configured.
- **Rate limiting**: no `RequestRateLimiter` gateway filter, `RedisRateLimiter` bean, or
  `KeyResolver` exists anywhere in this module. Despite Redis being wired in (`CacheConfig`,
  `spring-boot-starter-data-redis`), it is not used for rate limiting.

## Tests

- `ApiGatewayApplicationTests.java` — single `@SpringBootTest` `contextLoads()` smoke test, no
  assertions beyond successful context startup.
- `BaseIntegrationTest.java` was removed (GH #45) — it was dead Testcontainers scaffolding
  (spun up a `redis:7-alpine` container) with no subclass, and nothing in this module actually
  uses the `RedisCacheManager` `CacheConfig` defines (`@Cacheable` appears nowhere in
  `src/main`), so a Redis Testcontainer never backed anything worth asserting on.
- `Infrastructure/filter/JwtAuthFilterTest.java` (GH #45) — new plain-Mockito unit test of
  `JwtAuthFilter` (no Docker/Spring context): public-path bypass, missing/malformed
  `Authorization` header → 401, failed token validation → 401, and the
  `X-User-Name`/`X-User-ID`/`X-User-Role` headers forwarded to the mutated exchange on
  success. `ReactiveUserServiceClient` and `JwtService` are mocked; the exchange is built
  with `MockServerHttpRequest`/`MockServerWebExchange` and the outcome verified with
  `StepVerifier` (the filter is reactive, returns `Mono<Void>`).
- `Infrastructure/configuration/Resilience4jConfigurationTest.java` (GH #52) — plain unit test
  of `Resilience4jConfiguration.defaultCustomizer()`: builds a real
  `ReactiveResilience4JCircuitBreakerFactory`, applies the customizer, creates a circuit
  breaker for an id not covered by any YAML instance list, and asserts the resulting
  `CircuitBreakerConfig` matches the documented default thresholds — pins the Java bean's
  numbers against future drift from the YAML defaults.
- Still no tests for `SecurityConfig`, route predicates, the fallback controller, or
  `ReactiveUserServiceClient` directly.
- Run: `mvn -pl api-gateway test` (per root `CLAUDE.md`). No longer needs Docker for anything
  in this module's own test suite now that `BaseIntegrationTest` is gone.

## Gotchas

1. **Hardcoded JWT secret in source, duplicated across modules.**
   `api-gateway/src/main/java/.../constants/JwtConstants.java:7` and
   `user-service/src/main/java/.../constants/JwtConstants.java:7` contain the identical literal
   HS256 key, committed to git, not sourced from env/secret store. Anyone with read access to
   the repo can forge valid tokens.
2. **`JwtAuthFilter.isPublicPath` uses substring matching, `SecurityConfig` uses exact
   `pathMatchers`.** `JwtAuthFilter.java:79-85` treats any path *containing* e.g.
   `/actuator/info` as public (so `/api/v1/product/actuator/info/x` would skip JWT validation),
   while `SecurityConfig.java:27-35` only exempts the exact matcher forms. The two lists are
   independently maintained and can drift; the net behavior for an unanticipated path depends on
   which layer runs first.
3. ~~Two divergent route tables with no single source of truth.~~ **Fixed (GH #52).**
   `application.yml` (default, used when no profile is set — e.g. `docker-compose.yaml`, k8s
   deployment) and `application-local.yml` (`local` profile — `docker-compose.dev.yml`) used to
   define different route sets: default had a leftover `httpbin` test route to an external
   host and lacked the `frontend-service` catch-all; local had the catch-all (no
   filters/CB/retry at all) and dropped `httpbin`; `user-address-service` was a separate route
   id in default but merged into `user-service`'s predicate list in local. `httpbin` is now
   removed outright, both profiles carry the same `frontend-service` catch-all (default needs
   it for the k8s Ingress path — see Gotcha 6 — local doesn't but it's harmless there), and
   `user-address-service` is a separate id in both. See "Gateway routes" above.
4. **k8s Service/Deployment target the wrong port.**
   `k8s/services/api-gateway/api-gateway-deployment.yaml:33` sets `containerPort: 8080` and
   `k8s/services/api-gateway/api-gateway-service.yaml:11` sets `targetPort: 8080`, but the app
   listens on `8765` per `application.yml:107` and `Dockerfile:39` (`EXPOSE 8765`,
   `HEALTHCHECK ... http://localhost:8765/actuator/health`). Traffic routed through the k8s
   Service would hit a closed port.
5. **k8s ConfigMap's inline `application.yml` is never mounted.**
   `k8s/services/api-gateway/api-gateway-configmap.yaml:9-49` defines an alternate route table
   (using plain-name URIs like `http://user-service`, OpenFeign client config, Eureka disabled)
   as a ConfigMap data key, but `api-gateway-deployment.yaml` only consumes
   `SPRING_DATA_REDIS_HOST`/`SPRING_DATA_REDIS_PORT` from the ConfigMap via `env.valueFrom` —
   there is no `volumeMounts`/`volumes` wiring the `application.yml` key into the container. The
   entire ConfigMap route table is dead configuration.
6. **k8s deployment sets no `SPRING_PROFILES_ACTIVE`, `USER_SERVICE_URL`, etc.** So in k8s the
   pod runs on the default `application.yml` profile with default-valued service URLs
   (`http://user-service:8080` etc.). (The `httpbin` test route that used to also be active here
   in "production-shaped config" was removed — GH #52.) This is also exactly why the
   `frontend-service` catch-all route had to be added to the default profile rather than left
   local-only: the k8s Ingress forwards `/` in its entirety to this gateway with no separate
   frontend Ingress rule, and k8s always runs this (default) profile.
7. ~~`resilience4j.circuitbreaker.instances` in `application.yml` omits `order-service`.~~
   **Fixed (GH #52).** `order-service` is now listed alongside `user-service`,
   `product-service`, `payment-service`, matching the `order-service` route
   (`application.yml`) that names that CB.
8. ~~Two independent, slightly different circuit-breaker default configs.~~ **Fixed (GH #52).**
   `Resilience4jConfiguration.defaultCustomizer()`
   (`Infrastructure/configuration/Resilience4jConfiguration.java`), `application.yml`'s
   `resilience4j.circuitbreaker.configs.default`, and `application-local.yml`'s equivalent block
   now all set the identical values (`failureRateThreshold=50`, `slowCallRateThreshold=50`,
   `slowCallDurationThreshold=2s`, `waitDurationInOpenState=10s`,
   `permittedNumberOfCallsInHalfOpenState=3`, `slidingWindowSize=10`, `minimumNumberOfCalls=5`).
   `Resilience4jConfigurationTest` pins the Java bean's numbers so a future edit to one source
   and not the others fails a test instead of silently drifting again. Which source actually
   wins for a named instance vs. an unlisted id no longer matters since they're numerically
   identical either way.
9. **`resilience4j.retry.*` YAML config appears to be dead.** `application.yml:134-148` (and the
   `resilience4j-retry` Maven dependency, `pom.xml:74-78`) configure a Resilience4j retry
   registry, but the actual per-route retry behavior comes from Spring Cloud Gateway's own
   `Retry` `GatewayFilterFactory` with inline `args` (`retries: 3`, `methods: GET,POST`) — no
   code in this module invokes Resilience4j's retry API. `application-local.yml` has no
   `resilience4j.retry` block at all, which supports the reading that it's unused.
10. ~~`resilience4j.timelimiter` only configured in the `local` profile.~~ **Fixed (GH #52).**
    `application.yml` now has the same `timelimiter` block (5s timeout,
    `cancelRunningFuture: true`) for the same four service instances, so the default profile
    no longer silently falls back to Resilience4j's built-in 1s timeout.
11. ~~`@EnableFeignClients` with zero `@FeignClient` interfaces.~~ Removed (issue #51) —
    no `@FeignClient` interface ever existed anywhere in `api-gateway/src/main`. Downstream
    calls in this module use `WebClient` (`ReactiveUserServiceClient`) instead. The
    `spring-cloud-starter-openfeign`/`feign-micrometer` dependencies were left in `pom.xml`
    (not part of issue #51's explicit list for this module — only the duplicate
    `spring-boot-starter-actuator` entry was, see item 21).
12. ~~`@LoadBalanced WebClient.Builder` bean is never injected.~~ Removed (issue #51) — it
    had no consumer in this module; `ReactiveUserServiceClient` uses the plain
    `webClientBuilder` bean, which is now the only bean `WebClientConfig` defines.
13. **Caching infrastructure configured but entirely unused.** `CacheConfig` defines a full
    Redis cache manager plus two `RedisTemplate` beans and `@EnableCaching`, but no
    `@Cacheable`/`@CacheEvict`/manual template usage exists anywhere in `api-gateway/src/main`
    (confirmed by grep). Either dead code or a caching feature that was never finished.
14. **No CORS configuration.** Despite `frontend-service` being a browser-facing SSR client that
    calls through this gateway (`CLAUDE.md`), no CORS policy is configured anywhere in
    `api-gateway`. If the frontend and gateway are ever served from different origins in a
    browser-driven (non-SSR/proxy) call, requests would be blocked by the browser; this has
    presumably not surfaced because `frontend-service`'s calls are server-to-server, not
    browser-to-gateway.
15. **No rate limiting.** No `RequestRateLimiter` filter or `KeyResolver` exists, despite Redis
    being available and would be the natural backing store (`RedisRateLimiter`). The gateway has
    no protection against request floods beyond the circuit breakers, which protect *backends*,
    not the gateway itself.
16. **`UserDto.Role` field breaks Java field-naming convention** (`Infrastructure/http/dto/UserDto.java:18`:
    `private String Role;`, capitalized, unlike `name`/`id`/`username`). Lombok `@Data`
    generates `getRole()`/`setRole()` (standard accessor names despite the odd field name), but
    Jackson's default field-based property naming would bind the JSON key `Role` rather than
    `role` unless user-service's serializer normalizes case — not verified here since that's a
    different module.
17. **`UserDto.Role` is fetched but never used for authorization.** `JwtAuthFilter` builds
    `new UsernamePasswordAuthenticationToken(userDetails, null, null)` — the third
    argument (authorities) is `null`, so the role is never converted into a
    `GrantedAuthority`, and no `X-User-Role` (or similar) header is propagated downstream
    (only `X-User-Name` and `X-User-ID` are added, `JwtAuthFilter.java:58-59`). Role data is
    fetched from user-service on every request and then discarded.
18. **`/fallback` route auth status is ambiguous/likely unreachable externally as intended.**
    `FallbackController` (`FallbackController.java`) is not in `SecurityConfig`'s `permitAll`
    list, so a direct external `GET /fallback` would be rejected by
    `anyExchange().authenticated()` before reaching the controller (unless the caller already
    holds a valid JWT). It is only actually invoked via the gateway's internal
    `forward:/fallback` from the `CircuitBreaker` filter, which is a server-side forward that
    bypasses the WebFilter chain's exchange re-evaluation in the way route filters typically do
    — exact interaction between `forward:` and the security filter chain is not verified here.
19. **`BaseIntegrationTest` was removed for GH #45, then restored.** It was deleted outright
    (nothing exercised the Redis container it stood up at the time), but GH #53's new
    `CorsConfigurationIntegrationTest`/`RequestRateLimiterIntegrationTest` need a real Redis
    Testcontainer for the rate limiter, so it was restored (with the same Redis-container
    shape) rather than each test standing up its own. See Tests.
20. **Partially fixed: test coverage now exists for `JwtAuthFilter`** (GH #45,
    `Infrastructure/filter/JwtAuthFilterTest.java`) **and CORS/rate-limiting** (GH #53,
    `Infrastructure/configuration/{CorsConfigurationIntegrationTest,RateLimiterConfigTest,RequestRateLimiterIntegrationTest}.java`).
    `SecurityConfig` (beyond CORS), `JwtService`, `ReactiveUserServiceClient`, the fallback
    controller, and the route predicates/filters still have none.
21. ~~`spring-boot-starter-actuator` declared twice~~ in `pom.xml` — harmless (Maven
    dedupes) but indicated copy-paste drift; the second declaration was removed
    (issue #51).
22. ~~`bootstrap.properties` references Spring Cloud Config Server and Eureka~~ — removed
    (issue #50). It configured `spring.cloud.config.discovery.*`,
    `eureka.client.serviceUrl.defaultZone`, and `spring.config.import=optional:configserver:...`,
    but neither `spring-cloud-config-client` nor `spring-cloud-starter-netflix-eureka-client`
    was ever a declared dependency in `pom.xml`, so the properties had no effect — this project
    uses Kubernetes DNS for service discovery, not Eureka/Config Server.
23. **`Infrastructure/` package is capitalized**, deviating from the root `CLAUDE.md` layered
    convention (`infrastructure/`, lower-case) that other modules follow. `CLAUDE.md` explicitly
    calls out `product-service`'s `infastructure/` misspelling as a known, deliberately
    unfixed deviation but does not mention this one.
