# Architecture documentation — design

Retroactive Architecture Decision Records, a pattern catalog, and a guided reading path,
so the owner of this codebase can read it head to toe and understand why each thing is the
way it is.

## Problem

Much of this codebase was generated rather than authored. The owner's words: *"i started
this off to learn spring microservices but used ai for boilerplate and too much crud. kind
of bit me in the ass as it caused more trouble than it solved."*

The consequence is not just excess surface area — it is **missing provenance**. Normally an
engineer knows why the code looks the way it does because they made the choices: they read
the options and picked one. Here that reasoning was never formed, so there is no way to
tell, by reading, which shapes are load-bearing and which are arbitrary defaults nobody
evaluated.

That gap has real cost. A 2026-08-04 live smoke test found ~14 bugs, and the majority sat
at seams between generated pieces that were never reconciled — a UI that thinks in
`Product`s wired to a cart that thinks in `ProductVariation`s, DTOs on two sides of an HTTP
call with incompatible nullability contracts, a `@Cacheable` method whose null return
crashed against a cache configured to reject nulls. Each was individually small. None was
findable by reading one file.

Existing documentation does not close this gap:

| Artifact | Answers | Does not answer |
|---|---|---|
| `.claude/ai_docs/*.md` | What each module contains | Why it is shaped that way |
| `lessons/*.html` | Standalone concepts (filters, RBAC) | How they apply here specifically |
| `learning-records/*.md` | What the owner took away | The system as a whole |
| `RESOURCES.md` | Where to read more | Anything about this code |

## Goals

1. For every significant decision embedded in this codebase, record what forced the choice,
   what the alternatives were, what this code actually does, and what it costs.
2. Make explicit which decisions were **deliberate**, which were **inherited defaults**, and
   which are **accidental** — so the owner knows where they have freedom to change things.
3. Map the design patterns present here to their real instances in the code.
4. Provide a reading order that turns seven modules into one traceable request path.
5. Every claim verified against code, with a `file:line` citation.

## Non-goals

- Changing any code. This effort documents what exists on the day it is written.
- Replacing `.claude/ai_docs/`. Those stay as module reference; ADRs cross-link to them
  rather than duplicating their content.
- Tutorial teaching of Spring/Java fundamentals. `lessons/` and `RESOURCES.md` cover that;
  ADRs assume the reader knows what a filter is and explain why *this* filter exists here.
- Prescribing future architecture. Where a decision looks wrong, the ADR says so in
  Consequences and stops. Proposals belong in specs, not ADRs.

## Artifacts

```
docs/architecture/
├── README.md            index, how to use this, and the reading path entry point
├── reading-path.md      one request traced end to end, in the order to read it
├── patterns.md          pattern catalog -> real instances in this code
└── decisions/
    ├── 0001-<slug>.md
    ├── 0002-<slug>.md
    └── ...
```

### ADR format

```markdown
# NNNN. <Title>

**Status:** Deliberate | Inherited | Accidental
**Date recorded:** YYYY-MM-DD
**Affects:** <modules>

## Context
What forced a choice here. The constraint, not the solution.

## Options
The alternatives that genuinely existed, each with its trade-off. Two to four.

## What this codebase does
The actual mechanism, with `file:line` citations.

## Consequences
What this buys, what it costs, and what it has already cost — including any
bug this shape has actually produced.

## See also
Links to `.claude/ai_docs/<module>.md`, related ADRs, `RESOURCES.md` entries.
```

### The Status field

This is the highest-value element and the reason the format deviates from a standard ADR.

- **Deliberate** — genuinely chosen, with reasoning that can be reconstructed. Example:
  both the gateway and each service validate the JWT independently (GH #17/#19), which
  traces directly to the owner's stated RBAC mission.
- **Inherited** — a framework, generator, or tutorial default that was never evaluated.
  Example: `spring.jpa.hibernate.ddl-auto: update` with no migration tool. It works until
  it doesn't, and nobody decided it.
- **Accidental** — emerged from a mistake and is now load-bearing. Example:
  `OrderItem.productSku` is a `UUID` holding a ProductVariation id; that name directly
  caused a bug where `retrieveProduct()` was called with a variation id.

A retroactive ADR that presented every choice as deliberate would be fiction. Marking
provenance honestly is what makes the set usable for deciding what to change.

## Decision inventory

Approximately 23 ADRs. Final numbering is assigned during writing; grouping is for
planning only.

**Architecture (8)**
1. Microservices split by domain vs. modular monolith
2. API gateway as the sole client entry point
3. Synchronous Feign/HTTP between services vs. events/messaging
4. Database-per-service — note `docker-compose.yaml` and `docker-compose.dev.yml` disagree
   on this, which is itself the finding
5. Stateless JWT auth, validated independently at gateway and at each service
6. Identity propagated via headers derived from verified token claims, not client input
7. Server-side rendered frontend (Thymeleaf + HTMX) vs. SPA
8. Kubernetes DNS for service resolution instead of Eureka/Config Server

**Resilience (4)**
9. Circuit breaker per downstream dependency, with fallback routes
10. Retry policy split: gateway retries GET only; Feign-level `@Retry` for the two
    deliberately idempotent inventory writes
11. Saga with compensating transactions for order creation, rather than a distributed
    transaction
12. Idempotency via a persisted deduction ledger keyed on `orderItemId`

**Persistence (5)**
13. Domain models kept separate from JPA entities, with hand-written mappers
14. The four-layer package convention (`application` / `domain` / `dataAccess` /
    `infrastructure`)
15. Server-assigned `@GeneratedValue` ids — and the `merge()`-vs-`persist()` trap that
    broke both order and cart creation
16. Pessimistic locking for inventory mutation
17. `ddl-auto: update` with no migration tool

**Caching (2)**
18. Redis cache-aside via `@Cacheable`, with `disableCachingNullValues`
19. Cache eviction strategy — `allEntries` vs. keyed

**Testing (2)**
20. Testcontainers for integration tests, and the singleton-container lifecycle
21. `@WebMvcTest` slices that exclude security filters

**Operations (2)**
22. Two Docker Compose layouts plus Kubernetes manifests
23. Configuration via discrete env vars rather than a mounted `application.yml`

### Pattern catalog

`patterns.md` maps each pattern to its real instance, with citations:

Chain of Responsibility (both filter chains) · Facade (application services) ·
Adapter/Mapper (entity↔domain, domain↔DTO) · Repository · DTO · API Gateway ·
Saga / Compensating Transaction (`OrderServiceImpl.create`) · Circuit Breaker · Retry ·
Idempotency Key (the deduction ledger) · Cache-Aside · Template Method
(`OncePerRequestFilter`) · Proxy (Spring AOP — and why self-invocation silently bypasses
it, which constrains how `@Transactional` and `@Cacheable` can be called)

### Reading path

`reading-path.md` traces a single request — a product page view, then an add-to-cart —
through every layer it touches, naming the file to open at each step: browser → gateway
`SecurityWebFilterChain` → `JwtAuthFilter` → route predicate → `RequestRateLimiter` →
circuit breaker → downstream service filter → controller → application service → domain
service → mapper → repository → JPA → and back out.

The goal is that after one pass, the reader can place any file in the system without
searching.

## Accuracy requirements

Documentation that is wrong is worse than none, because the reader cannot tell. The
existing `ai_docs` already carried stale claims that had to be corrected mid-session on
2026-08-04 (several described bugs as unfixed that had been fixed, and one described a k8s
port mismatch that no longer existed).

Therefore:

1. **Every factual claim is verified against code at writing time.** Not from memory, not
   from another document — from the file.
2. **Every mechanism claim cites `file:line`.**
3. **Where a documented behavior was verified by running it** (a test, a live request), the
   ADR says so.
4. **Where the code contradicts an existing `ai_doc`**, the ADR notes the discrepancy rather
   than silently picking one; the `ai_doc` gets corrected as part of the same work.
5. **No claim that a thing is "correct" or "best practice" without a citation** in
   `RESOURCES.md` or an inline source link.

## Worked examples from real failures

Each of the ~14 bugs found and fixed on 2026-08-04 is a pattern misapplied, and belongs
inside the relevant ADR as a concrete failure case — pattern, misapplication, observed
symptom, fix. Notably:

| Bug | ADR it illustrates |
|---|---|
| Gateway `JwtAuthFilter` calling user-service unauthenticated | 5 — stateless JWT validation |
| Gateway retrying `POST /orders`, racing itself | 10 — retry policy |
| Client-supplied id sending `save()` through `merge()` | 15 — server-assigned ids |
| `@Cacheable` null crashing `disableCachingNullValues` | 18 — cache-aside |
| `retrieveProduct(variationId)` | 13 — domain/entity separation, and the Product/Variation seam |
| `restoreStock` with no ceiling | 12 — idempotency ledger |
| `@Container` on a shared static field restarting the DB per class | 20 — Testcontainers |

These are the most valuable content in the set: they are this codebase's own patterns
failing in its own code, not textbook examples.

## Sequencing — waves with review checkpoints

The set is written in waves, and **the owner reviews each wave before the next is
written.** This is not merely batching for size; it exists because the audience is a single
reader whose comprehension is the entire success criterion, and only that reader can say
whether an ADR lands at the right altitude.

In particular the `Status: Deliberate | Inherited | Accidental` field is a deliberate
deviation from standard ADR form, invented for this situation. If it does not earn its
place, that must surface after two or three ADRs, not after twenty-three.

| Wave | Contents | Checkpoint question |
|---|---|---|
| 1 | `README.md`, ADR template, `reading-path.md`, and 2–3 sample ADRs chosen to span the range — one **Deliberate** (stateless JWT), one **Inherited** (`ddl-auto: update`), one **Accidental** (`OrderItem.productSku`) | Does the format work? Is the altitude right — too deep, too shallow? Does `Status` help? |
| 2 | Remaining architecture ADRs (1–8) | Is cross-referencing to `ai_docs` pitched correctly? |
| 3 | Resilience + persistence ADRs (9–17), where most real failures live | Are the worked failure examples the most useful part, and should they get more room? |
| 4 | Caching, testing, operations ADRs (18–23) | Anything missing from the inventory now that most of it exists? |
| 5 | `patterns.md` — last, since it cross-references every ADR | Final read of the whole set. |

Wave 1 is deliberately small and spans the full range of the format, so a single short read
tells the owner whether the approach is right. Each subsequent wave is independently useful
even if the effort stops there.

Feedback from a checkpoint applies forward to unwritten waves, and retroactively to written
ones where the change is structural (e.g. dropping or renaming the `Status` field would be
applied across everything already written).

## Relationship to other work

Fully independent of the buyer spine slice
(`2026-08-04-buyer-spine-design.md`). This documents what exists today; that builds what is
next. Neither blocks the other.

One interaction worth noting: if the buyer spine lands first, ADRs 13 and 15 must describe
the Product/Variation seam as closed rather than open. Whichever order they run in, the
ADRs describe the code as it stands when written, and carry their **Date recorded**.
