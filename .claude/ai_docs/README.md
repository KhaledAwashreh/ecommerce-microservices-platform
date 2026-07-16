# ai_docs

Durable reference documentation for this repository.

## Cross-cutting

| Doc | Covers |
|-----|--------|
| [architecture.md](architecture.md) | System topology, request flow, auth flow, service dependency graph |
| [conventions.md](conventions.md) | Layering rules, naming, error handling, caching, testing conventions |
| [infrastructure.md](infrastructure.md) | Docker Compose, Kubernetes manifests, GitHub Actions pipelines |

## Per module

| Doc | Module |
|-----|--------|
| [api-gateway.md](api-gateway.md) | `api-gateway` |
| [user-service.md](user-service.md) | `user-service` |
| [product-service.md](product-service.md) | `product-service` |
| [order-service.md](order-service.md) | `order-service` |
| [payment-service.md](payment-service.md) | `payment-service` |
| [frontend-service.md](frontend-service.md) | `frontend-service` |
| [common.md](common.md) | `common` |

## Rules for these docs

1. Describe what the code **is**, not what it should be. Aspirational content belongs in
   the README roadmap.
2. Every claim must be traceable to a file. Cite paths as `module/src/.../File.java`.
3. Record gotchas and known deviations explicitly — they are the highest-value content.
4. Keep them current: a change to a module's public surface updates its doc in the same
   commit.
