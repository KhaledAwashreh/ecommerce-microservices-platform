---
name: add-endpoint
description: Add a new REST endpoint to a backend service in this repo, wiring every layer and every consumer. Use when adding or changing an endpoint on user-service, product-service, order-service, or payment-service.
---

# Add an endpoint

Adding an endpoint touches more than a controller. Work through the layers in order and
do not stop at the first one that compiles.

## 1. Confirm scope first

Ask before assuming: which service owns it, what the request and response shapes are,
what auth it requires, and whether any other service or the frontend must call it.

## 2. Provider service

1. `constants/ApiPaths.java` — add the path constant. Do not hardcode the path string in
   the annotation.
2. `application/dto/` — request and response DTOs. Add Bean Validation annotations
   (`@NotNull`, `@Size`, …) on the request DTO.
3. `domain/service/` — declare the operation on the interface, implement in
   `domain/service/impl/`. Business rules live here, not in the controller.
4. `dataAccess/repository/` — add query methods if needed.
5. `application/mapper/` — DTO to domain mapping. `dataAccess/mapper/` — domain to entity.
6. `application/controller/` — thin. Map, delegate, return. No repository or entity access.
7. Errors — throw the domain exception; let `GlobalExceptionHandler` translate it. Do not
   return raw error strings.

## 3. Gateway

If the endpoint sits under a path prefix the gateway does not already route, add the
route to the `api-gateway` configuration, including any path-rewrite filter and the
Resilience4j circuit breaker name. A missing gateway route surfaces as a 404 that looks
like a service bug.

## 4. Consumers

- Other services calling it — add the method to their `@FeignClient` interface with a
  path that matches the provider exactly.
- `frontend-service` — add the method to the matching `client/*ServiceClient`, and a
  controller handler plus Thymeleaf template if it is user-facing. Every frontend client
  call goes through the gateway base URL, not a service host.

## 5. Verify

```bash
mvn -pl <module> -am test
```

Then re-read the provider path and each consumer path side by side. Verb, path,
singular/plural, and DTO field names must match exactly — Jackson mismatches fail
silently as nulls.

## 6. Document

Update `.claude/ai_docs/<module>.md`: the HTTP API table, and the outbound dependencies
section of every consumer you touched.
