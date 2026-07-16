# payment-service

## Purpose

Records payments against orders and exposes a CRUD-ish HTTP API for processing, retrieving,
and refunding them. There is no real payment gateway integration: `PaymentServiceImpl`
always creates a payment with `amount = BigDecimal.ZERO` and `status = COMPLETED`, so
"processing" is not simulated in any meaningful sense (no success/failure branching, no
randomness, no gateway call). Consumed by `order-service` via a Feign client
(`PaymentClient`), which is itself routed through the API gateway.

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
│   ├── entity/PaymentEntity.java           # @Entity, table "payment"
│   └── mapper/PaymentMapper.java           # PaymentEntity <-> Payment (domain)
├── domain/
│   ├── model/Payment.java                  # domain POJO + PaymentStatus enum
│   ├── service/PaymentService.java         # interface
│   └── service/impl/PaymentServiceImpl.java# only implementation, "SIMULATED" gateway
```

No `infrastructure/` package exists in this module — despite `spring-cloud-starter-openfeign`
and `spring-cloud-starter-loadbalancer` being on the classpath (see pom.xml), payment-service
declares no `@FeignClient` and no `@EnableFeignClients`. It is a pure inbound REST + JPA
service; it calls nothing else itself.

## Domain model

`domain/model/Payment.java` (Lombok `@Data @Builder @AllArgsConstructor @NoArgsConstructor`):

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | |
| `orderId` | `UUID` | |
| `buyerId` | `UUID` | |
| `amount` | `BigDecimal` | Always `BigDecimal.ZERO` in practice — see Gotchas |
| `paymentMethod` | `String` | free text, no enum/validation |
| `status` | `Payment.PaymentStatus` | enum: `PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED, CANCELLED` |
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
  (not null), `buyer_id` (not null), `amount` (not null), `payment_method`, `status` (not
  null, `EnumType.STRING`), `transaction_id`, `payment_gateway`, `created_at`
  (`@CreationTimestamp`), `updated_at` (`@UpdateTimestamp`).
- `PaymentRepository extends JpaRepository<PaymentEntity, UUID>`:
  - `findByOrderId(UUID orderId): Optional<PaymentEntity>` — used by
    `getPaymentByOrderId`. **Not** used to prevent duplicate payments in `processPayment`
    (no idempotency check — see Gotchas).
  - `findByTransactionId(String transactionId): Optional<PaymentEntity>` — declared but never
    called anywhere in the module (dead code); moot anyway since `transactionId` is never set.
- No unique constraint on `order_id`, so multiple `PaymentEntity` rows can exist per order.

## HTTP API

Base path: `ApiPaths.BASE_PATH = "/api/v1/payment"` (singular — matches the gateway route
`Path=/api/v1/payment/**` in `api-gateway/src/main/resources/application.yml:95` and
`application-local.yml:73`, per the plural→singular fix in commit `fa6d426`).

`payment-service/src/main/java/.../application/controller/PaymentController.java`:

| Method | Path (`BASE_PATH` + …) | Request | Response | Status codes | Auth |
|---|---|---|---|---|---|
| POST | `/process` | `PaymentRequestDto` (JSON body: `orderId`, `buyerId`, `amount`, `paymentMethod`) | `PaymentResponseDto` | 200 always (no error branch in controller/service) | None enforced in this module — JWT is validated upstream at the gateway (`architecture.md`), payment-service trusts the caller |
| GET | `/{paymentId}` | path var `paymentId: UUID` | `PaymentResponseDto` or empty body | 200, 404 if not found | None |
| GET | `/order/{orderId}` | path var `orderId: UUID` | `PaymentResponseDto` or empty body | 200, 404 if not found | None |
| POST | `/{paymentId}/refund` | path var `paymentId: UUID` | `Boolean` (`true`/`false`) | 200 always (`true` if payment found and set to `REFUNDED`, `false` if not found) | None |

Notes:
- `ApiPaths.PAYMENT_BY_ID = "/{paymentId}"` and `ApiPaths.PAYMENT_BY_ORDER = "/order/{orderId}"`
  are distinguishable to Spring's path matcher only because `/order/...` has a literal
  first segment; both are registered as `@GetMapping` under the same controller.
- `processPayment` ignores `PaymentRequestDto.amount` entirely — see Gotchas.
- `refundPayment` does not validate the payment's current status before flipping it to
  `REFUNDED` (e.g. a `PENDING` or already-`REFUNDED` payment can be "refunded" again).

## Outbound dependencies

None. Despite `spring-cloud-starter-openfeign`, `spring-cloud-starter-loadbalancer`, and
`resilience4j-retry` being declared in `pom.xml`, and `com.stripe:stripe-java:24.0.0` being
present as a dependency, the module contains no Feign client, no `@EnableFeignClients`, no
retry annotation, and no reference to the Stripe SDK anywhere under `src/main`. These
dependencies are dead weight (see Gotchas).

payment-service is itself an inbound dependency of `order-service`
(`order-service/.../infrastructure/http/client/PaymentClient.java`, `@FeignClient(name =
"payment-service")`), which calls all four endpoints above through the gateway.

## Configuration

| Property | `application.yml` (default) | `application-local.yml` | `application-ide.yml` | `application-test.yml` |
|---|---|---|---|---|
| `server.port` | `8080` | (inherits 8080) | `8084` | `0` (random, for parallel test runs) |
| `spring.datasource.url` | `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/paymentdb}` | `jdbc:postgresql://localhost:5433/paymentdb` | `jdbc:postgresql://localhost:5436/paymentdb` | `jdbc:postgresql://localhost:5432/paymentdb` (overridden by Testcontainers at runtime) |
| `spring.datasource.username`/`password` | `postgres`/`test1234` (env-overridable) | `postgres`/`test1234` | `postgres`/`test1234` | `test`/`test` |
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

`payment-service/src/test/java/.../BaseIntegrationTest.java` is the only test file in the
module. It is an **abstract** base class (`@SpringBootTest @Testcontainers
@ActiveProfiles("test")`) that spins up a `postgres:16-alpine` Testcontainer and wires
`spring.datasource.*` + forces `ddl-auto=create-drop` via `@DynamicPropertySource`. **No
concrete test class extends it and no `@Test`-annotated method exists anywhere in the
module** — running `mvn -pl payment-service test` executes zero tests. The Docker daemon
requirement documented in the root `CLAUDE.md` ("Integration tests use TestContainers")
does not currently exercise anything for this module.

To run (produces "no tests found"): `mvn -pl payment-service test`

## Gotchas

1. **`amount` is silently discarded.** `PaymentRequestDto.amount` is read by nobody:
   `PaymentController.processPayment` (`application/controller/PaymentController.java:26-30`)
   calls `paymentService.processPayment(orderId, buyerId, paymentMethod)` — the 3-arg
   `PaymentService` interface (`domain/service/PaymentService.java:9`) has no `amount`
   parameter at all. `PaymentServiceImpl.processPayment`
   (`domain/service/impl/PaymentServiceImpl.java:37`) hardcodes
   `.amount(BigDecimal.ZERO) // Would be fetched from order`. Every payment ever created by
   this module has `amount = 0`, regardless of what the client (order-service) sends.
2. **No simulated gateway logic exists.** The task brief asks what determines success vs.
   failure in the "simulated gateway" — there is none. `PaymentServiceImpl.processPayment`
   unconditionally sets `status = Payment.PaymentStatus.COMPLETED` and
   `paymentGateway = "SIMULATED"` (a literal string constant, not read from config). No
   randomness, no failure path, no `PaymentStatus.FAILED`/`PENDING`/`PROCESSING` is ever
   produced by `processPayment`. `FAILED`, `PENDING`, `PROCESSING`, `CANCELLED` are unreachable
   dead enum values from this code path (only `COMPLETED` and, via `refundPayment`,
   `REFUNDED` are ever written).
3. **`transactionId` is never generated.** Neither `PaymentServiceImpl` nor `PaymentMapper`
   ever sets `Payment.transactionId` / `PaymentEntity.transaction_id`. It is always `null` in
   the database and in every `PaymentResponseDto`.
   `PaymentRepository.findByTransactionId` (`dataAccess/dao/PaymentRepository.java:15`) is
   therefore permanently dead code — nothing ever populates a value it could look up.
4. **No idempotency on `processPayment`.** `PaymentServiceImpl.processPayment`
   (`domain/service/impl/PaymentServiceImpl.java:31-50`) never checks
   `paymentRepository.findByOrderId(orderId)` before inserting. Calling `POST
   /api/v1/payment/process` twice for the same `orderId` (e.g. a client retry) creates two
   separate `payment` rows with no unique constraint on `order_id` to stop it.
5. **No request validation.** `PaymentRequestDto` (`application/dto/PaymentRequestDto.java`)
   has no Bean Validation annotations (`@NotNull`, `@Positive`, etc.) despite
   `spring-boot-starter-validation` being a declared dependency (`pom.xml:41`), and
   `PaymentController.processPayment` does not annotate the parameter with `@Valid`. A
   `null` `orderId`/`buyerId`/`paymentMethod` or a negative `amount` is accepted without
   error (amount is discarded anyway, per item 1).
6. **Dead/unused dependencies in `pom.xml`.** `spring-cloud-starter-loadbalancer`,
   `spring-cloud-starter-openfeign`, `resilience4j-retry`, and `com.stripe:stripe-java`
   (`pom.xml:48-52, 59-65, 76-80`) are declared but nothing under `src/main` uses Feign,
   load-balanced `RestTemplate`/`WebClient`, `@Retry`, or the Stripe SDK. This module makes
   no outbound calls at all.
7. **Two parallel `PaymentStatus` enums.** `Payment.PaymentStatus`
   (`domain/model/Payment.java:38-45`) and `PaymentEntity.PaymentStatus`
   (`dataAccess/entity/PaymentEntity.java:57-59`) are separately declared with identical
   values, bridged only by `Enum.valueOf(name())` in `PaymentMapper`. Any future edit to one
   enum's value set without the matching edit to the other fails at runtime with
   `IllegalArgumentException`, not at compile time.
8. **`refundPayment` has no state-machine guard.**
   `PaymentServiceImpl.refundPayment` (`domain/service/impl/PaymentServiceImpl.java:69-81`)
   sets any found payment's status to `REFUNDED` unconditionally — a `PENDING`,
   `FAILED`, or already-`REFUNDED` payment can be "refunded" and the caller gets `true` back
   with no indication anything unusual happened.
9. **`createdAt`/`updatedAt` set on the domain object are thrown away.**
   `PaymentServiceImpl.processPayment` sets `.createdAt(Instant.now()).updatedAt(Instant.now())`
   on the `Payment` builder (`domain/service/impl/PaymentServiceImpl.java:41-42`), but
   `PaymentMapper.toEntity` (`dataAccess/mapper/PaymentMapper.java:29-42`) never copies those
   fields onto `PaymentEntity` — they're re-derived by Hibernate's `@CreationTimestamp`/
   `@UpdateTimestamp` instead. Functionally harmless (values end up nearly identical) but the
   manual assignment in the service is dead/misleading.
10. **No auth/authorization in this module.** See Security section — any direct caller that
    can reach `payment-service:8080` (bypassing the gateway) can process, view, or refund any
    payment for any order/buyer with no credential check.
11. **Zero executable tests.** `BaseIntegrationTest.java` is abstract and unused by any
    concrete test class — `mvn -pl payment-service test` runs nothing (see Tests section).
12. **Misleading Dockerfile comment.** `Dockerfile:34` claims the port is dynamically
    assigned via `server.port=0`, which is only true for the `test` profile; the image's
    actual runtime config (`application.yml`) fixes `server.port: 8080`.
