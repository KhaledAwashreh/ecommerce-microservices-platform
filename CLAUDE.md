# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

`ecommerce-microservices-platform` — a Spring Boot 3 / Java 21 microservices e-commerce
platform. Maven multi-module reactor, Docker Compose for local dev, Kubernetes manifests
for deployment, GitHub Actions for CI/CD.

Group ID: `com.kawashreh.ecommerce`. Root artifact: `ecommerce-microservices-platform`
(packaging `pom`).

## Modules

| Module | Purpose | Deep docs |
|--------|---------|-----------|
| `api-gateway` | Spring Cloud Gateway. Routing, JWT auth filter, Resilience4j circuit breakers, fallbacks. | [.claude/ai_docs/api-gateway.md](.claude/ai_docs/api-gateway.md) |
| `user-service` | Users, accounts, addresses, roles. JWT issuance, Argon2 password hashing. | [.claude/ai_docs/user-service.md](.claude/ai_docs/user-service.md) |
| `product-service` | Products, variations, categories, inventory, reviews. | [.claude/ai_docs/product-service.md](.claude/ai_docs/product-service.md) |
| `order-service` | Carts, orders, order items, discounts. Orchestrates product + payment via Feign. | [.claude/ai_docs/order-service.md](.claude/ai_docs/order-service.md) |
| `payment-service` | Payment records and simulated gateway processing. | [.claude/ai_docs/payment-service.md](.claude/ai_docs/payment-service.md) |
| `frontend-service` | Server-side rendered web client (Thymeleaf + HTMX, port 3000). Calls everything through the gateway. The README's "React / Node.js" label is wrong. | [.claude/ai_docs/frontend-service.md](.claude/ai_docs/frontend-service.md) |
| `common` | Shared error DTO and exception types. | [.claude/ai_docs/common.md](.claude/ai_docs/common.md) |

Cross-cutting docs live in [.claude/ai_docs/](.claude/ai_docs/) — see `architecture.md`
for the request flow across services.

## Layered package convention

Backend services follow the same four-layer split. Match it when adding code.

```
application/      controller, dto, mapper (HTTP mappers), application services
domain/           model (POJOs), service + service.impl, enums, exception
dataAccess/       entity (JPA), repository or dao, mapper (entity <-> domain)
infrastructure/   cache config, http clients (Feign/WebClient), security, config
constants/        ApiPaths, CacheConstants, JwtConstants
```

Notes and known deviations:
- `product-service` uses `infastructure/` (misspelled), `dataAccess/Dao/` (capital D), and a
  third stray `infra/models/` package. `api-gateway` uses capitalized `Infrastructure/`.
  Do not "fix" these casually — the rename touches imports across the module.
- Domain models are plain classes, deliberately separate from JPA entities. Mapping
  between them belongs in `dataAccess/mapper`. HTTP DTO mapping belongs in
  `application/mapper`.
- Controllers must not touch repositories or entities directly.

## Build and test

```bash
mvn clean install              # whole reactor
mvn -pl order-service test     # one module
mvn -pl order-service -am test # one module plus its dependencies
mvn clean verify               # what CI runs (main.yml), includes JaCoCo
```

Integration tests use TestContainers and need a running Docker daemon.

## Running locally

```bash
docker-compose -f docker-compose.dev.yml up -d    # full stack
docker-compose -f docker-compose.dev.yml down
./start.sh -b                                     # convenience wrapper (rebuilds)
```

Entry points once up:
- API Gateway — http://localhost:8765
- Frontend — http://localhost:3000
- Zipkin — http://localhost:9411
- RedisInsight — http://localhost:5540
- PostgreSQL — localhost:5433 (container port 5432)

`docker-compose.yaml` (unified single-Postgres layout, mirrors k8s) and
`docker-compose.dev.yml` (dev layout) are both present and differ. Check which one a
change affects before editing.

## Inter-service communication

- Clients call the **API gateway** (`:8765`), never a service directly.
- Service-to-service calls go through OpenFeign, addressed by Kubernetes DNS service name
  (`http://product-service:8080`), wrapped in Resilience4j circuit breakers and retries.
- `frontend-service` uses Feign clients pointed at the gateway base URL, with
  `BearerTokenInterceptor` attaching the session JWT to every outbound call.
- Auth is JWT. `user-service` issues, `api-gateway` validates in `JwtAuthFilter`, then
  propagates identity headers downstream.

## Conventions

- Java 21, Spring Boot 3.x. Constructor injection, no field `@Autowired`.
- Public API paths are centralised per module in `constants/ApiPaths.java`. Add routes
  there rather than hardcoding strings in annotations.
- Error handling is **not** uniform today. Only `user-service` and `frontend-service` have a
  `GlobalExceptionHandler`; only `user-service` returns `common`'s `ErrorResponse`.
  `order-service`, `product-service`, and `payment-service` fall back to Spring's default
  error body, and the gateway's fallback returns plain text. Use `ErrorResponse` for new
  handlers, but do not assume callers receive that shape.
- IDs are `UUID` across services.
- Caching is Redis-backed via each module's `CacheConfig`; cache names live in
  `CacheConstants`.
- Never commit secrets. `.env` and compose files carry dev-only credentials
  (`test1234`) — do not reuse those values anywhere real.

## Working agreements

- Read the module's `.claude/ai_docs/<module>.md` before changing that module.
- When a change spans a service boundary (new endpoint, changed DTO), update: the
  provider, the Feign/WebClient interface on every consumer, the gateway route, and the
  relevant ai_doc.
- After a rename or refactor, grep the whole repo — including `src/test` and the
  `k8s/` and `.github/` manifests — before calling it done.
- Prefer surgical edits. Do not reformat untouched code.
- `target/` and `coverage/` are build output. Never edit them.
