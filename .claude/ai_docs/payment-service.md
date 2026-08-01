# payment-service

> **Amendment (GH #17 fix):** this doc predates the fix and the "No
> auth/authorization in this module... no Spring Security dependency, no auth filter"
> statements are now **stale** — this was the worst-case service cited in GH #17
> (direct network access previously allowed processing/viewing/refunding arbitrary
> payments). Current state: `infrastructure/security/JwtAuthFilter` + `JwtService`
> (new `jjwt` dependency, same version as user-service/api-gateway) validate every
> request's bearer token locally (except `/actuator/**`) before it reaches a
> controller, using the same shared HMAC secret (`constants/JwtConstants`, new). A new
> `IncomingAuthHeaderFeignInterceptor` forwards the caller's Authorization header onto
> this service's outbound Feign calls to order-service. The rest of this document is
> otherwise still accurate as of the fix; it has not been fully regenerated.

## Purpose

Records payments against orders and exposes a CRUD-ish HTTP API for processing, retrieving,
and refunding them. There is no real payment gateway integration: `PaymentServiceImpl`
unconditionally sets `status = COMPLETED`, so "processing" is not simulated in any
meaningful sense (no success/failure branching, no randomness, no real gateway call). The
`amount` **is** authoritative, though: `processPayment` re-derives it from order-service
(via `OrderServiceClient`, a Feign client) instead of trusting the caller — fixed in issue
#10; previously it was hardcoded to `BigDecimal.ZERO`. `processPayment` is also idempotent
per `orderId` (issue #11): a repeat call for an order that already has a payment returns the
existing row instead of creating a second one, and a DB-level unique constraint stops
concurrent duplicates that race past the in-process check. `refundPayment` enforces a status
precondition (issue #12): only a `COMPLETED` payment can be refunded, so a second refund or a
refund of a non-`COMPLETED` payment is rejected (`409`) instead of silently reported as
successful. Consumed by `order-service` via a Feign client (`PaymentClient`), which is itself
routed through the API gateway.

## Package layout

```
payment-service/src/main/java/com/kawashreh/ecommerce/payment_service/
├── PaymentServiceApplication.java          # @SpringBootApplication entry point
├── application/
│   ├── controller/PaymentController.java   # REST endpoints
│   ├── dto/PaymentRequestDto.java          # inbound: orderId, buyerId, amount, paymentMethod
│   ├── dto/PaymentResponseDto.java         # outbound: full Payment view incl. status/txnId
│   └── mapper/PaymentHttpMapper.java       # Payment (domain) -> PaymentResponseDto
├── constants/ApiPaths.java                 # BASE_PATH + relative route fragments
├── dataAccess/
│   ├── dao/PaymentRepository.java          # Spring Data JPA repo
│   ├── entity/PaymentEntity.java           # @Entity, table "payment", unique on order_id
│   └── mapper/PaymentMapper.java           # PaymentEntity <-> Payment (domain)
├── domain/
│   ├── exception/OrderServiceException.java# thrown when the order-service lookup fails (#10)
│   ├── exception/InvalidPaymentStateException.java # thrown by refundPayment on an illegal status transition (#12)
│   ├── model/Payment.java                  # domain POJO + PaymentStatus enum
│   ├── service/PaymentService.java         # interface
│   └── service/impl/PaymentServiceImpl.java# only implementation, "SIMULATED" gateway
└── infrastructure/
    ├── config/FeignClientConfig.java       # @EnableFeignClients
    └── http/
        ├── client/OrderServiceClient.java  # @FeignClient("order-service"), retrieveOrder(id)
        ├── client/OrderServiceErrorDecoder.java
        └── dto/OrderDto.java, OrderItemDto.java  # Feign response shapes
```

As of issue #10, payment-service does declare a `@FeignClient` (`OrderServiceClient`) and an
`infrastructure/` package, wired via `FeignClientConfig`'s `@EnableFeignClients`. It is no
longer a pure inbound-only service: `PaymentServiceImpl.processPayment` calls out to
order-service to resolve the authoritative payment amount before persisting (see Outbound
dependencies).

## Domain model

`domain/model/Payment.java` (Lombok `@Data @Builder @AllArgsConstructor @NoArgsConstructor`):

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | |
| `orderId` | `UUID` | |
| `buyerId` | `UUID` | |
| `amount` | `BigDecimal` | Derived by `PaymentServiceImpl.resolveOrderAmount` from order-service's item unit prices × quantities (issue #10); the caller-supplied `PaymentRequestDto.amount` is always ignored — see Gotchas |
| `paymentMethod` | `String` | free text, no enum/validation |
| `status` | `Payment.PaymentStatus` | enum: `PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED, CANCELLED`. The only transition the code ever performs is `COMPLETED -> REFUNDED`, enforced by `refundPayment` (issue #12) — see Gotchas item 8 |
| `transactionId` | `String` | never populated anywhere in the module — always `null` |
| `paymentGateway` | `String` | hardcoded to the literal `"SIMULATED"` in `PaymentServiceImpl.processPayment` |
| `createdAt` / `updatedAt` | `Instant` | set manually in the domain builder before save, but overwritten by Hibernate `@CreationTimestamp`/`@UpdateTimestamp` since `PaymentMapper.toEntity` never copies them onto the entity (see Gotchas) |

`PaymentEntity` (`dataAccess/entity/PaymentEntity.java`) mirrors the domain model 1:1 plus JPA
annotations, and declares its own duplicate `PaymentStatus` enum (`PENDING, PROCESSING,
COMPLETED, FAILED, REFUNDED, CANCELLED`) rather than reusing the domain enum.
`PaymentMapper.mapStatusToDomain`/`mapStatusToEntity` translate between the two enums by name
via `Enum.valueOf`.

## Persistence

- Table: `payment` (`@Table(name = "payment")`, `PaymentEntity`).
- Schema managed by Hibernate `ddl-auto`, not SQL migrations:
  - `application.yml` (default/docker profile): `update`
  - `src/test/resources/application-test.yml`: `create-drop`
  - `BaseIntegrationTest` also force-overrides `spring.jpa.hibernate.ddl-auto` to
    `create-drop` via `@DynamicPropertySource`.
- Columns: `id` (PK, `@GeneratedValue`, default strategy = `GenerationType.AUTO`), `order_id`
  (not null, **unique** — `uk_payment_order_id`, issue #11), `buyer_id` (not null), `amount`
  (not null), `payment_method`, `status` (not null, `EnumType.STRING`), `transaction_id`,
  `payment_gateway`, `created_at` (`@CreationTimestamp`), `updated_at` (`@UpdateTimestamp`).
- `PaymentRepository extends JpaRepository<PaymentEntity, UUID>`:
  - `findByOrderId(UUID orderId): Optional<PaymentEntity>` — used by `getPaymentByOrderId`
    **and**, as of issue #11, by `processPayment` as an idempotency fast-path check (before
    the order-service lookup) and again as the fallback lookup after a unique-constraint
    violation on a concurrent insert.
  - `findByTransactionId(String transactionId): Optional<PaymentEntity>` — declared but never
    called anywhere in the module (dead code); moot anyway since `transactionId` is never set.
- **Unique constraint on `order_id`** (issue #11): `PaymentEntity`'s `@Table` declares
  `uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_id", columnNames =
  "order_id")`. Since this module has no Flyway/Liquibase, the constraint is applied the same
  way the rest of the schema is — Hibernate `ddl-auto: update` is expected to issue the
  `ALTER TABLE ... ADD CONSTRAINT` at startup.
  **Unverified, and worth checking against any long-lived database.** `hibernate.hbm2ddl.halt_on_error`
  defaults to `false` and is not set here, so if that `ALTER TABLE` fails — which it will on any
  database that already contains duplicate `order_id` rows, exactly the state this bug produced
  before the fix — Hibernate logs the failure and boots anyway, leaving no constraint and no
  startup error. Confirm the constraint exists (`\d payment` in psql) rather than assuming it.
  When present, this is what actually stops two concurrent `processPayment`
  calls for the same order from both inserting; the `findByOrderId` check in the service layer
  only narrows the race window, it can't close it by itself. `processPayment` uses
  `paymentRepository.saveAndFlush(...)` (not plain `save`) so the constraint violation
  surfaces synchronously as a `DataIntegrityViolationException` at the call site instead of
  being deferred to a later flush/commit where it could no longer be caught; on catching it,
  the service re-queries `findByOrderId` and returns the winning row rather than a 500. No
  `@Transactional` spans the check-then-insert in `processPayment` — each repository call gets
  its own transaction, which matters on Postgres: if the insert and the fallback lookup shared
  one transaction, the constraint violation would abort that whole transaction and the
  fallback lookup would fail too.

## HTTP API

Base path: `ApiPaths.BASE_PATH = "/api/v1/payment"` (singular — matches the gateway route
`Path=/api/v1/payment/**` in `api-gateway/src/main/resources/application.yml:95` and
`application-local.yml:73`, per the plural→singular fix in commit `fa6d426`).

`payment-service/src/main/java/.../application/controller/PaymentController.java`:

| Method | Path (`BASE_PATH` + …) | Request | Response | Status codes | Auth |
|---|---|---|---|---|---|
| POST | `/process` | `PaymentRequestDto` (JSON body: `orderId`, `buyerId`, `amount`, `paymentMethod`; `@Valid` since GH #40 — `orderId`/`buyerId` `@NotNull`, `paymentMethod` `@NotBlank`) | `PaymentResponseDto` (existing payment if one already exists for `orderId` — issue #11) | 200 on success, 400 on `@Valid` failure (Spring's default body, no `GlobalExceptionHandler` in this module); an unhandled `OrderServiceException` from the order-service lookup (issue #10) surfaces as Spring's default 500 body | None enforced in this module — JWT is validated upstream at the gateway (`architecture.md`), payment-service trusts the caller |
| GET | `/{paymentId}` | path var `paymentId: UUID` | `PaymentResponseDto` or empty body | 200, 404 if not found | None |
| GET | `/order/{orderId}` | path var `orderId: UUID` | `PaymentResponseDto` or empty body | 200, 404 if not found | None |
| POST | `/{paymentId}/refund` | path var `paymentId: UUID` | `Boolean` (`true`/`false`) | 200 (`true` if payment found and refunded, `false` if not found); 409 if the payment exists but is not `COMPLETED` (including one already `REFUNDED`) — issue #12 | None |

Notes:
- `ApiPaths.PAYMENT_BY_ID = "/{paymentId}"` and `ApiPaths.PAYMENT_BY_ORDER = "/order/{orderId}"`
  are distinguishable to Spring's path matcher only because `/order/...` has a literal
  first segment; both are registered as `@GetMapping` under the same controller.
- `processPayment` ignores `PaymentRequestDto.amount` entirely — see Gotchas.
- Calling `POST /process` twice for the same `orderId` no longer creates a second row
  (issue #11): the repeat call short-circuits on `findByOrderId` and returns the original
  payment, without re-calling order-service. Concurrent requests that both pass that check
  are resolved by the DB-level unique constraint on `order_id` (see Persistence).
- `refundPayment` now enforces a status precondition (issue #12): only a `COMPLETED`
  payment can be refunded. Any other current status — `PENDING`, `PROCESSING`, `FAILED`,
  `CANCELLED`, or an already-`REFUNDED` payment — throws `InvalidPaymentStateException`
  (`domain/exception/InvalidPaymentStateException.java`, unchecked), which
  `PaymentController.refundPayment` catches locally and maps to `409 CONFLICT` (body
  `false`). A payment that doesn't exist still returns `false`/200, unchanged from before —
  that's a different case ("not found") from an illegal transition ("found, but not
  eligible"), and only the latter changed in this fix.

## Outbound dependencies

As of issue #10, payment-service has one outbound dependency: `OrderServiceClient`
(`infrastructure/http/client/OrderServiceClient.java`, `@FeignClient(name =
"order-service")`), which calls `GET {ApiPaths.ORDER_BASE}{ApiPaths.ORDER_BY_ID}` to fetch
the order and derive the payment amount from its selected items. It is wrapped in a
Resilience4j circuit breaker and retry (config name `order-service`, `application.yml`) and
a custom `OrderServiceErrorDecoder`. `com.stripe:stripe-java:24.0.0` remains a declared but
unused dependency — no reference to the Stripe SDK exists anywhere under `src/main` (see
Gotchas).

payment-service is itself an inbound dependency of `order-service`
(`order-service/.../infrastructure/http/client/PaymentClient.java`, `@FeignClient(name =
"payment-service")`), which calls all four endpoints above through the gateway.

## Configuration

| Property | `application.yml` (default) | `application-local.yml` | `application-ide.yml` | `application-test.yml` |
|---|---|---|---|---|
| `server.port` | `8080` | (inherits 8080) | `8084` | `0` (random, for parallel test runs) |
| `spring.datasource.url` | `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/paymentdb}` | `jdbc:postgresql://localhost:5433/paymentdb` | `jdbc:postgresql://localhost:5436/paymentdb` | `jdbc:postgresql://localhost:5432/paymentdb` (overridden by Testcontainers at runtime) |
| `spring.datasource.username`/`password` | `postgres`/*(none — required)* | `postgres`/`test1234` | `postgres`/`test1234` | `test`/`test` |
| `spring.jpa.hibernate.ddl-auto` | `update` | (inherits) | (inherits) | `create-drop` |
| `management.zipkin.tracing.endpoint` | `${ZIPKIN_BASE_URL:http://zipkin:9411}/api/v2/spans` | `http://zipkin:9411/api/v2/spans` | `http://localhost:9411/api/v2/spans` | (inherits) |
| `management.tracing.sampling.probability` | `1.0` | (inherits) | (inherits) | (inherits) |
| `GATEWAY_URL` | — | — | `http://localhost:8765` (declared but not read by any `@Value`/`@ConfigurationProperties` found in this module) | — |

`application-ide.yml` is meant for running payment-service from an IDE while the rest of the
stack runs in Docker (per its header comment), hence the distinct port `8084` and DB port
`5436`.

The Dockerfile comment "Port is dynamically assigned (server.port=0 in
application.properties)" (`Dockerfile:34`) is inaccurate for the image it actually builds:
the default `application.yml` used at container runtime sets `server.port: 8080`, not `0`.
`server.port: 0` only appears in `application-test.yml`.

## Caching

None. No `CacheConfig`, no `@Cacheable`, no Redis dependency in this module.

## Security

None implemented in payment-service itself. No Spring Security dependency, no auth
annotations, no header/token checks in `PaymentController`. The module relies entirely on
the API gateway having already validated the JWT before routing here (per
`architecture.md`/root `CLAUDE.md`). Any caller with network access to
`payment-service:8080` directly (bypassing the gateway) can hit every endpoint
unauthenticated.

## Tests

`payment-service/src/test/java/.../BaseIntegrationTest.java` is an **abstract** base class
(`@SpringBootTest @Testcontainers @ActiveProfiles("test")`) that spins up a
`postgres:16-alpine` Testcontainer and wires `spring.datasource.*` + forces
`ddl-auto=create-drop` via `@DynamicPropertySource`. It now has a concrete subclass,
`PaymentServiceIntegrationTest` (GH #45) — see below.

`domain/service/impl/PaymentServiceImplTest.java` (added alongside issues #10 and #11,
extended for #12) is a plain-Mockito unit test of `PaymentServiceImpl` — no Spring context,
no Docker/Testcontainers needed. It covers: amount derivation from order items and
`OrderServiceException` on a missing order / empty items / missing unit price / Feign
failure (#10); the idempotency fast-path, the concurrent-insert unique-constraint race
(caught and resolved to the winning row), and the edge case where the constraint fires but no
row can be found afterwards (#11); and `refundPayment`'s status guard (#12) — a successful
refund from `COMPLETED`, `false` on an unknown `paymentId`, and `InvalidPaymentStateException`
on every other status via a parameterized test over `PaymentEntity.PaymentStatus` (excluding
`COMPLETED`), plus an explicit already-`REFUNDED` case. Because `PaymentRepository` is fully
mocked here, this test can't observe the idempotency behavior actually being backed by the
DB's unique constraint on `order_id`, only the pre-check + catch-`DataIntegrityViolationException`
code path in isolation.

`PaymentServiceIntegrationTest.java` (GH #45) is the first concrete `BaseIntegrationTest`
subclass in this module: `@Autowired PaymentService`/`PaymentRepository` exercised against a
real `postgres:16-alpine` Testcontainer, with only `OrderServiceClient` (the Feign call to
order-service) mocked via `@MockitoBean`. Covers `processPayment` persisting a real row with
the amount derived from the mocked order, the idempotency retry actually hitting the DB
(not just the in-memory mock), and the refund transition guard against real persisted state.

`application/controller/PaymentControllerTest.java` (GH #40) is a `@WebMvcTest` slice
(JwtAuthFilter excluded from component scanning) covering the new `@Valid` on
`PaymentRequestDto` — see Gotcha 5, now fixed.

To run: `mvn -pl payment-service test` (needs Docker for `PaymentServiceIntegrationTest`;
24 tests total across `PaymentServiceImplTest`, `PaymentServiceIntegrationTest`, and
`PaymentControllerTest`)

## Gotchas

1. **`amount` is intentionally discarded — by design, since issue #10.**
   `PaymentRequestDto.amount` is still read by nobody: `PaymentController.processPayment`
   (`application/controller/PaymentController.java`) never passes it through, and the 3-arg
   `PaymentService` interface (`domain/service/PaymentService.java`) has no `amount`
   parameter. This is deliberate now rather than a bug: `PaymentServiceImpl.processPayment`
   calls `resolveOrderAmount(orderId)`, which fetches the order via `OrderServiceClient` and
   sums `unitPrice * quantity` across its `selectedItems`, so a client can no longer dictate
   what it pays. `resolveOrderAmount` throws `OrderServiceException` (unchecked) if the order
   is missing, has no items, has an item with no unit price, or the Feign call fails for any
   reason — no payment row is persisted when it throws.
2. **No simulated gateway logic exists.** The task brief asks what determines success vs.
   failure in the "simulated gateway" — there is none. `PaymentServiceImpl.processPayment`
   unconditionally sets `status = Payment.PaymentStatus.COMPLETED` and
   `paymentGateway = "SIMULATED"` (a literal string constant, not read from config). No
   randomness, no failure path, no `PaymentStatus.FAILED`/`PENDING`/`PROCESSING` is ever
   produced by `processPayment`. `FAILED`, `PENDING`, `PROCESSING`, `CANCELLED` are unreachable
   dead enum values from this code path (only `COMPLETED` and, via `refundPayment`,
   `REFUNDED` are ever written) — still true after issue #12; `refundPayment` now *checks*
   for these statuses (to reject a refund) but nothing in this module ever *writes* them.
   Making them reachable would mean giving `processPayment` real success/failure branching
   (e.g. simulated random failure, or an explicit `PROCESSING` state before `COMPLETED`) —
   that's a change to `processPayment`'s behavior, not to `refundPayment`'s guard, and is
   genuinely separate from issue #12. Recommendation: track it as its own issue if the dead
   statuses need to be addressed; #12 only had to make `refundPayment` correctly *reject*
   payments in those statuses, which it now does regardless of whether they're reachable.
3. **`transactionId` is never generated.** Neither `PaymentServiceImpl` nor `PaymentMapper`
   ever sets `Payment.transactionId` / `PaymentEntity.transaction_id`. It is always `null` in
   the database and in every `PaymentResponseDto`.
   `PaymentRepository.findByTransactionId` (`dataAccess/dao/PaymentRepository.java:15`) is
   therefore permanently dead code — nothing ever populates a value it could look up.
4. **Idempotent on `orderId` — fixed in issue #11.** `PaymentServiceImpl.processPayment` now
   calls `paymentRepository.findByOrderId(orderId)` before doing anything else — before even
   the order-service lookup — and returns the existing payment unchanged if one exists. A
   client retry, a double-click, or the gateway's configured retry no longer creates a second
   `payment` row. Because the fast-path check alone is race-prone under concurrent requests,
   `PaymentEntity` also declares a DB-level unique constraint (`uk_payment_order_id` on
   `order_id`). If two requests race past the fast-path check, `saveAndFlush` (not plain
   `save`) makes the constraint violation surface synchronously as a
   `DataIntegrityViolationException`, which `processPayment` catches and resolves by
   re-querying `findByOrderId` and returning the winning row instead of a 500. Semantic
   choice: a repeat call returns the existing payment rather than being rejected as a
   conflict — partial/split payments per order aren't a concept this module supports today
   (one row per `orderId`, enforced by the constraint), so returning what the client's
   original request would have returned is the more useful idempotent behavior. If partial
   payments are ever intended, this needs an explicit idempotency key instead of keying off
   `orderId` alone.
5. ~~**No request validation.**~~ — fixed (GH #40). `PaymentRequestDto` now has
   `@NotNull` on `orderId`/`buyerId` and `@NotBlank` on `paymentMethod`;
   `PaymentController.processPayment` validates with `@Valid`. `amount` is deliberately
   left without a constraint - it's discarded anyway (per item 1 / the comment in
   `PaymentController`), so a negative/malformed value here still can't reach the DB.
6. **`com.stripe:stripe-java` is still a dead dependency.** Unlike
   `spring-cloud-starter-loadbalancer`, `spring-cloud-starter-openfeign`, and
   `resilience4j-retry` — all now exercised by `OrderServiceClient` and its circuit
   breaker/retry config (`application.yml`, issue #10) — nothing under `src/main` references
   the Stripe SDK anywhere. It remains declared but unused in `pom.xml`.
7. **Two parallel `PaymentStatus` enums.** `Payment.PaymentStatus`
   (`domain/model/Payment.java:38-45`) and `PaymentEntity.PaymentStatus`
   (`dataAccess/entity/PaymentEntity.java:57-59`) are separately declared with identical
   values, bridged only by `Enum.valueOf(name())` in `PaymentMapper`. Any future edit to one
   enum's value set without the matching edit to the other fails at runtime with
   `IllegalArgumentException`, not at compile time.
8. **`refundPayment` now enforces a state guard — fixed in issue #12.**
   `PaymentServiceImpl.refundPayment` requires the payment to be `COMPLETED`; every other
   status (`PENDING`, `PROCESSING`, `FAILED`, `CANCELLED`, or an already-`REFUNDED` payment)
   throws `InvalidPaymentStateException` instead of silently transitioning and reporting
   `true`. A missing payment is unchanged — still `false`, not an exception, since that's a
   "not found" case rather than an illegal transition on an existing entity. The
   controller catches `InvalidPaymentStateException` locally (no module-wide
   `GlobalExceptionHandler` was added — payment-service still has none, per root
   `CLAUDE.md`) and maps it to `409 CONFLICT`; any other unexpected exception from this
   endpoint still falls through to Spring's default 500 body, same as the rest of this
   module.
9. **`createdAt`/`updatedAt` set on the domain object are thrown away.**
   `PaymentServiceImpl.processPayment` sets `.createdAt(Instant.now()).updatedAt(Instant.now())`
   on the `Payment` builder (`domain/service/impl/PaymentServiceImpl.java:66-67`), but
   `PaymentMapper.toEntity` (`dataAccess/mapper/PaymentMapper.java:29-42`) never copies those
   fields onto `PaymentEntity` — they're re-derived by Hibernate's `@CreationTimestamp`/
   `@UpdateTimestamp` instead. Functionally harmless (values end up nearly identical) but the
   manual assignment in the service is dead/misleading.
10. **No auth/authorization in this module.** See Security section — any direct caller that
    can reach `payment-service:8080` (bypassing the gateway) can process, view, or refund any
    payment for any order/buyer with no credential check.
11. ~~**Integration-test path still exercises nothing.**~~ — fixed (GH #45).
    `PaymentServiceIntegrationTest` now extends `BaseIntegrationTest` and exercises
    `PaymentService` against a real Postgres Testcontainer (see Tests section).
12. **Misleading Dockerfile comment.** `Dockerfile:34` claims the port is dynamically
    assigned via `server.port=0`, which is only true for the `test` profile; the image's
    actual runtime config (`application.yml`) fixes `server.port: 8080`.
