# common

## Purpose

`common` is a shared Maven library (`com.kawashreh.ecommerce:common`, packaging `jar`) holding
one error DTO and two `RuntimeException` subclasses used to standardize error responses across
services. It has a single external dependency (`jackson-annotations`) and no Spring dependency of
its own — the classes are plain Java, framework-agnostic. In practice it is consumed by exactly
two of the six other modules.

A third exception, `IllegalArgumentException` (shadowing `java.lang.IllegalArgumentException`
by simple name), used to live here — repo-wide grep found nothing that threw, caught, or
imported it; every `throw new IllegalArgumentException(...)` in the repo resolved to the JDK
class instead. Removed as dead code (issue #51).

## Package layout

```
common/src/main/java/com/kawashreh/ecommerce/common/
├── dto/
│   └── ErrorResponse.java              # status, message, timestamp; Jackson-serializable
└── exceptions/
    ├── DuplicateEntityException.java   # extends RuntimeException
    └── NoSuchElementException.java     # extends RuntimeException — shadows java.util.NoSuchElementException
```

No `src/test` directory exists for this module.

## Domain model

| Class | File | Fields / ctors | Notes |
|---|---|---|---|
| `ErrorResponse` | `common/src/main/java/com/kawashreh/ecommerce/common/dto/ErrorResponse.java` | `int status`, `String message`, `LocalDateTime timestamp`; single `@JsonCreator` constructor, getters only (immutable, no setters) | Jackson-annotated (`@JsonCreator`/`@JsonProperty`) so it round-trips through `ObjectMapper` on both serialize and deserialize sides. |
| `DuplicateEntityException` | `.../exceptions/DuplicateEntityException.java` | `RuntimeException` subclass, `(String message)` and `(String message, Throwable cause)` ctors | Plain marker/carrier exception, no extra state. |
| `NoSuchElementException` (`com.kawashreh.ecommerce.common.exceptions`) | `.../exceptions/NoSuchElementException.java` | Same two ctors as above | Same simple name as `java.util.NoSuchElementException`. See Gotchas. |

No JPA entities, repositories, or persistence code in this module.

## Persistence

Not applicable — `common` has no persistence layer.

## HTTP API

Not applicable — `common` exposes no endpoints. `ErrorResponse` is the body shape one
`@RestControllerAdvice` (user-service) actually emits; see "Cross-module usage" below for how far
that shape actually travels.

## Outbound dependencies

None. `pom.xml` (`common/pom.xml`) declares only `com.fasterxml.jackson.core:jackson-annotations:2.18.0`.

## Configuration

None — no properties, no Spring context.

## Tests

None. No `common/src/test` directory exists.

## Gotchas

1. ~~`common.exceptions.IllegalArgumentException` is dead code.~~ Removed (issue #51) —
   repo-wide grep found nothing that threw, caught, or imported it; every
   `throw new IllegalArgumentException(...)` in the repo resolved to
   `java.lang.IllegalArgumentException` anyway (see Cross-module usage §2).
2. **`common.exceptions.NoSuchElementException` is used for authorization checks, not
   "not found."** In `user-service`, ownership-check failures are thrown as this exception
   (`UserServiceImpl.java:152`, `:187`; `AddressServiceImpl.java:86`, `:103`) and then mapped to
   HTTP 404 by `GlobalExceptionHandler.handleNotFound` (`user-service/.../exception/GlobalExceptionHandler.java:17-25`),
   even though the resource exists and the real condition is "you don't own this" (403-shaped, not
   404-shaped). The same exception/mapping is reused for login failure
   (`UserController.java:70`, `"Invalid username or password"` → 404), where 401 would be the
   conventional status.
3. **Only two of six consumer-capable modules depend on `common`.** `user-service` and
   `frontend-service` declare the dependency in their `pom.xml` and both genuinely use it — there
   is no "declares but doesn't use" case. `order-service`, `product-service`, `payment-service`,
   and `api-gateway` do not depend on `common` at all, and have no `GlobalExceptionHandler`
   equivalent (only `user-service` and `frontend-service` contain any `@ExceptionHandler`/`@ControllerAdvice`
   code — confirmed by repo-wide grep for those annotations).
4. **`ErrorResponse` is not actually a repo-wide error contract**, despite the root `CLAUDE.md`
   claim ("Errors surface through `GlobalExceptionHandler` using `common`'s `ErrorResponse`"). Only
   `user-service` returns it. `order-service`, `product-service`, and `payment-service` have no
   custom exception handler, so uncaught exceptions fall through to Spring Boot's default
   whitelabel `/error` JSON (`{timestamp, status, error, path}` — a different field set, and no
   `message` guarantee for arbitrary exceptions). `api-gateway`'s circuit-breaker fallback
   (`api-gateway/src/main/java/com/kawashreh/ecommerce/api_gateway/FallbackController.java:13-16`)
   returns a **plain-text** body (`"Service is currently unavailable..."`), not JSON at all.
   `frontend-service`'s `GlobalExceptionHandler.extractMessage`
   (`frontend-service/.../exception/GlobalExceptionHandler.java:60-74`) tries to
   `objectMapper.readValue(ex.contentUTF8(), ErrorResponse.class)` on every Feign error body; for
   any non-user-service backend this JSON-parse will fail (wrong shape or plain text) and silently
   fall back to the generic `"Service error"` string via the `catch (Exception e)` at line 71-73 —
   so real backend error messages from order-service/product-service/payment-service never reach
   the user, only user-service's do.
5. Sibling shadowing dead code discovered in the same investigation, and removed alongside it
   (issue #51, not part of `common` but directly adjacent): `user-service` used to define its
   own `com.kawashreh.ecommerce.user_service.exception.MethodArgumentNotValidException`, which
   shadowed `org.springframework.web.bind.MethodArgumentNotValidException` by simple name. It
   was never thrown or referenced anywhere — `GlobalExceptionHandler.java` imports and handles
   Spring's real class instead.

## Cross-module usage

### 1. Who actually depends on `common`

| Module | Declares dependency (`pom.xml`) | Actually imports `common` classes | Notes |
|---|---|---|---|
| `user-service` | Yes (`user-service/pom.xml:90-94`) | Yes — `ErrorResponse`, `DuplicateEntityException`, `NoSuchElementException` | Full use: throws both exceptions from the service layer and controller, renders `ErrorResponse` from `GlobalExceptionHandler`. |
| `frontend-service` | Yes (`frontend-service/pom.xml:106-110`) | Yes — `ErrorResponse`, `DuplicateEntityException`, `NoSuchElementException` | Uses `ErrorResponse` only to *deserialize* upstream Feign error bodies (`GlobalExceptionHandler.java:70`), not to produce its own response body (it returns `ModelAndView` redirects, never JSON). |
| `order-service` | No | No | Has its own local exceptions instead: `ProductServiceException`, `InsufficientStockException` (`order-service/src/main/java/com/kawashreh/ecommerce/order_service/domain/exception/`). No `GlobalExceptionHandler` — these become unhandled 500s with Spring's default error body. |
| `product-service` | No | No | No custom exception types found; repo-wide grep for `throw new` / `orElseThrow` in this module returned no matches — missing-entity cases appear to be handled by returning `null`/defaults rather than throwing (not fully audited beyond this grep). |
| `payment-service` | No | No | Same observation as `product-service`: no `throw new` / `orElseThrow` matches anywhere in the module. |
| `api-gateway` | No | No | Only error surface is `FallbackController` (`api-gateway/src/main/java/com/kawashreh/ecommerce/api_gateway/FallbackController.java`), a plain-text 503 body, unrelated to `ErrorResponse`. |

No module declares the `common` dependency without using it. Four of six modules use
"similar concepts" (custom exceptions, error responses) without depending on `common` at all,
each with its own ad hoc shape.

### 2. Shadowing: `common.exceptions.{IllegalArgumentException, NoSuchElementException}` vs JDK

`com.kawashreh.ecommerce.common.exceptions.IllegalArgumentException` shadows
`java.lang.IllegalArgumentException`, and `com.kawashreh.ecommerce.common.exceptions.NoSuchElementException`
shadows `java.util.NoSuchElementException`, by simple class name. Every usage site in the repo,
and which type each one actually resolves to:

**`IllegalArgumentException` usage sites — all resolve to `java.lang.IllegalArgumentException`:**

| File:Line | Resolves to | Why |
|---|---|---|
| `user-service/src/main/java/com/kawashreh/ecommerce/user_service/domain/model/User.java:60` | `java.lang` | No import of the common class in this file; module has a wildcard `java.util.*` import but no `common.exceptions` import. |
| `order-service/src/main/java/com/kawashreh/ecommerce/order_service/domain/service/impl/OrderServiceImpl.java:64,73,79,97,101,251` | `java.lang` | `order-service` does not even depend on `common` (not on its classpath), so this can only resolve to `java.lang`. |
| `frontend-service/src/main/java/com/kawashreh/ecommerce/frontend/exception/GlobalExceptionHandler.java:49-52` (`@ExceptionHandler(IllegalArgumentException.class)`) | `java.lang` | The file's imports (lines 3-19) include `common.dto.ErrorResponse`, `common.exceptions.DuplicateEntityException`, `common.exceptions.NoSuchElementException` — but **not** `common.exceptions.IllegalArgumentException`. Since the common class is never imported here, the bare name defaults to `java.lang`. |

Net effect: `common.exceptions.IllegalArgumentException` is unreachable dead code (confirms
Gotcha 1) — no call site imports it, so it can never be thrown or caught anywhere in the repo as
written.

**`NoSuchElementException` usage sites — all resolve to `com.kawashreh.ecommerce.common.exceptions.NoSuchElementException`:**

| File:Line | Resolves to | Why |
|---|---|---|
| `user-service/.../domain/service/impl/UserServiceImpl.java:3` (import), thrown at `:152`, `:187` | `common` | Explicit import line 3. |
| `user-service/.../domain/service/impl/AddressServiceImpl.java:3` (import), thrown at `:86`, `:103` | `common` | Explicit import line 3. |
| `user-service/.../application/controller/UserController.java:3` (import), thrown at `:70` | `common` | Explicit import line 3. |
| `user-service/.../exception/GlobalExceptionHandler.java:5` (import), caught at `:17` | `common` | Explicit import line 5; `@ExceptionHandler(NoSuchElementException.class)`. |
| `frontend-service/.../exception/GlobalExceptionHandler.java:6` (import), caught at `:44` | `common` | Explicit import line 6. |

No file in the repo relies on `java.util.NoSuchElementException` (e.g. via `Optional.get()`
throwing it implicitly, or catching it directly) — all `Optional` access observed in `user-service`
uses `.orElse(null)`/`.map(...)`, not `.get()` or `.orElseThrow()` with the JDK type. So the
`NoSuchElementException` shadow has not caused a mismatch in practice, but it is one import away
from doing so: adding `import java.util.NoSuchElementException;` to any of the five files above,
or removing the explicit `common` import, would silently change which exception type is thrown or
caught, since the compiler would not error — both are unchecked `RuntimeException` subtypes with
compatible constructors in most call patterns.

### 3. `ErrorResponse` vs each service's actual error shape

| Service | Has `GlobalExceptionHandler`? | Body shape returned | Matches `common.ErrorResponse`? |
|---|---|---|---|
| `user-service` | Yes (`user-service/.../exception/GlobalExceptionHandler.java`) | `{status, message, timestamp}` via `ErrorResponse`, for `NoSuchElementException` (404), `MethodArgumentNotValidException` (400), `DuplicateEntityException` (409), and catch-all `Exception` (500, message hardcoded to `"An unexpected error occurred"`) | Yes — exact match, it's the source of the shape. |
| `frontend-service` | Yes, but produces no JSON body | Redirects (`ModelAndView("redirect:...")`) with the error message URL-encoded as a query param; never serializes `ErrorResponse` outward, only reads it in from Feign responses | N/A — different response mechanism entirely (SSR redirect, not JSON). |
| `order-service` | No | Spring Boot default `/error` body: `{timestamp, status, error, path}` (framework default, not custom code) | No — different field set, no explicit `message` guarantee, `error` used instead of Jackson-mapped `message`. |
| `product-service` | No | Same Spring Boot default as `order-service` | No. |
| `payment-service` | No | Same Spring Boot default as `order-service` | No. |
| `api-gateway` | No (only `FallbackController`) | Plain text string, HTTP 503, no JSON | No — not even the same content type. |

Only `user-service` actually returns `common.ErrorResponse`. This directly undermines the
`frontend-service` Feign-error parsing described in Gotcha 4: `frontend-service` assumes every
upstream error body deserializes as `ErrorResponse`, but that is only true for `user-service`
responses.
