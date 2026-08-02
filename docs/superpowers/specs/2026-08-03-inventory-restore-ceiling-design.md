# Inventory restore ceiling — design (GH #30, remaining half)

## Problem

`InventoryServiceImpl.restoreStock` (product-service) was partially fixed at `38595d6`:
it now rejects non-positive quantities and acquires the same pessimistic lock
`deductStock` uses, closing the concurrency half of GH #30. The upper-bound half is
still open: `InventoryRepository.restoreQuantity` is an unconditional `+quantity`
update with no ceiling. Nothing today records how much stock was ever deducted for a
given order or order item, so there is no data to check a restore against.

The sole caller, `OrderServiceImpl.restoreDeductedInventory` (order-service's
compensating transaction for a failed order creation), only ever restores exactly
what it just deducted, by construction of an in-memory list — so this is a
trust-boundary hardening, not an active bug. The gap: `InventoryController.restoreStock`
is a public, unauthenticated endpoint that takes only `productVariationId` +
`quantity`, with no way to correlate a restore call back to a specific deduction.

## Goal

Give product-service a real, persisted ceiling on `restoreStock`: a restore can never
exceed what was actually deducted for the same reference, and duplicate/retried calls
are idempotent rather than double-applying.

## Data model

New entity in product-service: `InventoryDeductionEntity` (table `inventory_deduction`,
created automatically — this module uses `ddl-auto: update`, no Flyway/Liquibase, so no
migration file is needed).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | generated |
| `orderItemId` | UUID | **unique** — the ledger key |
| `productVariationId` | UUID | for debugging/reporting only, not part of the key |
| `deductedQuantity` | int | set once, at deduct time |
| `restoredQuantity` | int | starts at 0, incremented by each restore |
| `createdAt` / `updatedAt` | Instant | |

Lifecycle: created by `deductStock`, updated (never re-created) by `restoreStock`.
The invariant `restoredQuantity <= deductedQuantity` is the ceiling.

`orderItemId` was chosen as the key (over an `orderId` + `productVariationId`
composite) because order-service already loops per `OrderItem` when deducting or
restoring one `productVariationId` + `quantity` pair, and `OrderItem.id` is already a
stable, never-reused UUID.

## API / service-boundary changes

This crosses the product-service ↔ order-service boundary. Per this repo's working
agreement, every side gets updated: provider, Feign consumer, call sites, both ai_docs.
No gateway route changes — this is direct Feign, not routed through the gateway.

- **`InventoryController`** (product-service): `deductStock` and `restoreStock` both
  gain a required `orderItemId` query param.
  - `PUT /inventory/product-variation/{productVariationId}/deduct?quantity=&orderItemId=`
  - `PUT /inventory/product-variation/{productVariationId}/restore?quantity=&orderItemId=`
- **`InventoryService` / `InventoryServiceImpl`**: `deductStock(productVariationId,
  orderItemId, quantity)`, `restoreStock(productVariationId, orderItemId, quantity)`.
- **`InventoryDeductionRepository`** (new): `findByOrderItemId(UUID)` with
  `@Lock(PESSIMISTIC_WRITE)`, mirroring the existing lock pattern on
  `InventoryRepository`; plus `save`.
- **`ProductServiceClient`** (order-service Feign interface): `deductInventory` /
  `restoreInventory` gain an `orderItemId` param.
- **`OrderServiceImpl`**: `updateProductInventory` and `restoreDeductedInventory` pass
  `item.getId()` as `orderItemId`.
- Update `.claude/ai_docs/product-service.md` (HTTP API table + Gotcha #5, rewritten to
  say the ceiling is now real, not a documented gap) and `.claude/ai_docs/order-service.md`
  if it documents this call.

## Logic

**`deductStock`:**
1. Acquire inventory lock (existing).
2. Look up `findByOrderItemId(orderItemId)` (locked). If a row already exists, this is
   a retry: return `true` without touching quantity (idempotent — this is the new,
   in-scope deduct idempotency).
3. Otherwise, proceed with the existing guarded `deductQuantity` update. On success,
   insert the ledger row with `deductedQuantity = quantity, restoredQuantity = 0`.

**`restoreStock`:**
1. Reject non-positive quantity (existing).
2. Acquire inventory lock (existing).
3. Look up the ledger row by `orderItemId` (locked).
   - Missing → reject (`false`). A restore must correspond to a real deduction.
   - `restoredQuantity == deductedQuantity` → idempotent no-op, return `true`.
   - `restoredQuantity + quantity > deductedQuantity` → reject (`false`). **This is the
     ceiling firing — the actual fix for the remaining half of #30.**
   - Otherwise: perform `restoreQuantity` (existing unconditional add — now safe
     because it's pre-validated against the ledger) and increment `restoredQuantity`.

## Error handling

Stays within this module's existing convention (documented in
`ai_docs/product-service.md`): these endpoints already return `Boolean`/200 with no
4xx, not `common`'s `ErrorResponse`. All new rejection cases (ceiling exceeded, no
matching ledger row, non-positive quantity) return `false`; success and idempotent
no-ops return `true`. Introducing proper 4xx/`ErrorResponse` semantics for this module
is separate tech debt, out of scope for closing #30.

## Testing

Extend `InventoryServiceIntegrationTest`:
- `deduct_thenRestore_ledgerTracksCorrectly` — full happy path
- `deduct_calledTwiceSameOrderItemId_isIdempotent` — second deduct doesn't
  double-decrement
- `restore_exceedingDeductedAmount_isRejected` — the regression test for the
  remaining half of #30
- `restore_calledTwiceAfterFullRestore_isIdempotentNoOp`
- `restore_withNoMatchingLedgerEntry_isRejected`
- extend the existing concurrent-restore test to pass an `orderItemId` and assert the
  ceiling holds even under concurrent restore attempts

In order-service, `OrderServiceImplTest` mocks/verifications for `deductInventory` /
`restoreInventory` need updating to the new signature — mechanical but touches every
existing call site in that test file.

## Out of scope

- Proper error-response bodies (4xx + `ErrorResponse`) for InventoryController —
  separate tech debt.
- Authorization/ownership checks on the restore/deduct endpoints — a separate,
  already-documented class of gap in this module.
- A general audit/reporting UI over the ledger table — only the ceiling-check use case
  is being built.
