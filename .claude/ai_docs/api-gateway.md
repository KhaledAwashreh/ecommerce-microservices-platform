# api-gateway

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
`docker-compose.dev.yml` and the `local` folder in k8s). They differ (see Gotchas).

**`application.yml` (default profile) routes:**

| Route id | Predicate path(s) | Target URI (env override) | Filters | Circuit breaker / fallback |
|---|---|---|---|---|
| `httpbin` | `Path=/get` | `http://httpbin.org:80` (hardcoded, external) | `AddRequestHeader=MyHeader,MyURI`, `AddRequestParameter=Param,Value` | none |
| `user-service` | `Path=/api/v1/user/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `product-service` | `Path=/api/v1/product/**,/api/v1/productReview/**,/api/v1/categories/**,/api/v1/inventory/**,/api/v1/product-variation/**` | `${PRODUCT_SERVICE_URL:http://product-service:8080}` | `Retry` (3, GET/POST) | CB name `product-service` → `forward:/fallback` |
| `order-service` | `Path=/api/v1/orders/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` → `forward:/fallback` |
| `order-cart-service` | `Path=/api/v1/carts/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` (reused, same backend) → `forward:/fallback` — added for GH #13, same "separate route id, shared CB name" pattern as `user-role-service`/`user-address-service` |
| `user-role-service` | `Path=/api/v1/roles/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `user-address-service` | `Path=/api/v1/address/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `payment-service` | `Path=/api/v1/payment/**` | `${PAYMENT_SERVICE_URL:http://payment-service:8080}` | `Retry` (3, GET/POST) | CB name `payment-service` → `forward:/fallback` |

**`application-local.yml` routes:**

| Route id | Predicate path(s) | Target URI (env override) | Filters | Circuit breaker / fallback |
|---|---|---|---|---|
| `user-service` | `Path=/api/v1/user/**,/api/v1/address/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `product-service` | `Path=/api/v1/product/**,/api/v1/productReview/**,/api/v1/categories/**,/api/v1/inventory/**,/api/v1/product-variation/**` | `${PRODUCT_SERVICE_URL:http://product-service:8080}` | `Retry` (3, GET/POST) | CB name `product-service` → `forward:/fallback` |
| `user-role-service` | `Path=/api/v1/roles/**` | `${USER_SERVICE_URL:http://user-service:8080}` | `Retry` (3, GET/POST) | CB name `user-service` → `forward:/fallback` |
| `order-service` | `Path=/api/v1/orders/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` → `forward:/fallback` |
| `order-cart-service` | `Path=/api/v1/carts/**` | `${ORDER_SERVICE_URL:http://order-service:8080}` | `Retry` (3, GET/POST) | CB name `order-service` (reused) → `forward:/fallback` — added for GH #13 |
| `payment-service` | `Path=/api/v1/payment/**` | `${PAYMENT_SERVICE_URL:http://payment-service:8080}` | `Retry` (3, GET/POST) | CB name `payment-service` → `forward:/fallback` |
| `frontend-service` | `Path=/**` (catch-all) | `http://frontend-service:3000` (hardcoded, no filters) | none | none |

The `httpbin` test route (`application.yml:14-20`) and the `frontend-service` catch-all
(`application-local.yml:84-88`) exist in only one of the two profiles — see Gotchas.

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
| Gateway routes (all 5-6 backend routes) | Spring Cloud Gateway HTTP proxy | user/product/order/payment-service, env-overridable | end clients | Gateway `Retry` filter (3 attempts, GET/POST) + `CircuitBreaker` filter → `forward:/fallback` (503) |
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
| `resilience4j.circuitbreaker.configs.default.*` | `application.yml:117-125` | `failureRateThreshold=50`, `slowCallRateThreshold=50`, `waitDurationInOpenState=10000ms`, `slowCallDurationThreshold=2000ms`, `permittedNumberOfCallsInHalfOpenState=3`, `slidingWindowSize=10`, `minimumNumberOfCalls=5` | Duplicated (with different values in the `local` profile — see Gotchas) by the programmatic `Resilience4jConfiguration.defaultCustomizer()` bean, which hardcodes the **same numeric values except it omits `minimumNumberOfCalls`** |
| `resilience4j.circuitbreaker.instances.{user-service,product-service,payment-service}` | `application.yml:126-132` | `baseConfig: default` | `order-service` and `user-role-service`/`user-address-service` (which reuse the `user-service` CB name) are **not all explicitly listed** — `order-service` CB name is used by a route but has no explicit instance entry in the default profile |
| `resilience4j.retry.configs.default.*` | `application.yml:134-141` | `maxAttempts=3`, `waitDuration=1000ms`, retry on `IOException`/`ConnectException` | This registry is **not** what backs the per-route `Retry` gateway filter (that filter takes its own inline `args.retries`/`args.methods`); no code in this module invokes Resilience4j's `Retry` API directly, so this block appears to configure an unused registry |
| `resilience4j.timelimiter.*` | `application-local.yml:113-126` only | `timeoutDuration=5s`, per-service instances including `order-service` | Absent from default `application.yml` |
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
3. **Two divergent route tables with no single source of truth.** `application.yml` (default,
   used when no profile is set — e.g. `docker-compose.yaml`, k8s deployment) and
   `application-local.yml` (`local` profile — `docker-compose.dev.yml`) define different route
   sets: default has a leftover `httpbin` test route to an external host
   (`application.yml:14-20`) and lacks the `frontend-service` catch-all; local has the
   `frontend-service` catch-all (`application-local.yml:84-88`, no filters/CB/retry at all) and
   drops `httpbin`. `user-role-service`/`user-address-service` are separate route ids in default
   but merged into `user-service`'s predicate list in local.
4. **k8s Service/Deployment port** — `k8s/services/api-gateway/api-gateway-deployment.yaml`
   sets `containerPort: 8765` and `api-gateway-service.yaml` sets `targetPort: 8765`,
   matching the app's actual listen port (`application.yml:139`, `Dockerfile` `EXPOSE 8765`).
   This item previously described a `containerPort: 8080`/`targetPort: 8080` mismatch; that
   was fixed (issue #54) and this note is stale as of issue #60's investigation — left here
   so it isn't rediscovered as new.
5. **k8s ConfigMap's inline `application.yml` was never mounted (issue #60, fixed).**
   `api-gateway-configmap.yaml` used to define an alternate route table (using plain-name
   URIs like `http://user-service`, OpenFeign client config, Eureka disabled) as a ConfigMap
   data key, but `api-gateway-deployment.yaml` only ever consumed
   `SPRING_DATA_REDIS_HOST`/`SPRING_DATA_REDIS_PORT` from the ConfigMap via `env.valueFrom` —
   there was no `volumeMounts`/`volumes` wiring the `application.yml` key into the container,
   so the entire ConfigMap route table was dead configuration (and, separately, the
   `spring.cloud.openfeign.client.config` half of that block was *always* inert regardless of
   wiring — api-gateway has no `@FeignClient`/`@EnableFeignClients` anywhere, it only proxies
   via `spring.cloud.gateway.routes[].uri`). The dead block has been removed; see item 6 for
   what replaced it.
6. **k8s deployment now sets `USER_SERVICE_URL`/`PRODUCT_SERVICE_URL`/`ORDER_SERVICE_URL`/
   `PAYMENT_SERVICE_URL` (issue #60, fixed; previously unset).** The gateway's route `uri`s
   default to `${USER_SERVICE_URL:http://user-service:8080}` etc. — `:8080` is each backend
   pod's *container* port, but the in-cluster Services (`k8s/services/*/*-service.yaml`) all
   expose port 80, so an unset default can't actually route anywhere. Verified against a
   local `kind` cluster: `http://product-service:8080` times out from another pod,
   `http://product-service` (port 80) returns 200. `api-gateway-configmap.yaml` now sets all
   four env vars to the port-less in-cluster DNS names, wired into `api-gateway-deployment.yaml`
   via `configMapKeyRef`. No `SPRING_PROFILES_ACTIVE` is set, so the pod still runs the
   default `application.yml` profile with the `httpbin` test route present (unrelated,
   pre-existing — see item 3).
7. **`resilience4j.circuitbreaker.instances` in `application.yml` omits `order-service`.**
   `application.yml:126-132` lists `user-service`, `product-service`, `payment-service` only,
   even though the `order-service` route (`application.yml:50-62`) names CB `order-service`. It
   silently falls back to the programmatic default customizer
   (`Resilience4jConfiguration.java:16-28`), so behavior is likely fine, but the YAML instance
   list is inconsistent with the routes it's meant to document.
8. **Two independent, slightly different circuit-breaker default configs.**
   `Resilience4jConfiguration.defaultCustomizer()` (`Infrastructure/configuration/Resilience4jConfiguration.java:16-28`)
   hardcodes `failureRateThreshold=50`, `slowCallRateThreshold=50`,
   `slowCallDurationThreshold=2s`, `waitDurationInOpenState=10s`,
   `permittedNumberOfCallsInHalfOpenState=3`, `slidingWindowSize=10` (no
   `minimumNumberOfCalls`), while `application.yml`'s `resilience4j.circuitbreaker.configs.default`
   sets the same fields plus `minimumNumberOfCalls=5`, and `application-local.yml`'s equivalent
   block uses different values (`slidingWindowSize=20`, `slowCallRateThreshold=80`,
   `permittedNumberOfCallsInHalfOpenState=5`). Which one wins for a given profile is not
   verified here — both a Java `Customizer` bean and YAML-driven config exist simultaneously.
9. **`resilience4j.retry.*` YAML config appears to be dead.** `application.yml:134-148` (and the
   `resilience4j-retry` Maven dependency, `pom.xml:74-78`) configure a Resilience4j retry
   registry, but the actual per-route retry behavior comes from Spring Cloud Gateway's own
   `Retry` `GatewayFilterFactory` with inline `args` (`retries: 3`, `methods: GET,POST`) — no
   code in this module invokes Resilience4j's retry API. `application-local.yml` has no
   `resilience4j.retry` block at all, which supports the reading that it's unused.
10. **`resilience4j.timelimiter` only configured in the `local` profile**
    (`application-local.yml:113-126`), absent from default `application.yml` — inconsistent
    resilience behavior between profiles for the identical route set.
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
