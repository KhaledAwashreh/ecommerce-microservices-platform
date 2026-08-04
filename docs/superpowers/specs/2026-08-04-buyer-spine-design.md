# Buyer spine — design (Slice 1)

Browse → variation selection → cart → checkout → order, made to work end-to-end once,
properly.

## Problem

The platform has a wide, shallow surface: most entities have full CRUD, mappers in both
directions, and endpoints nothing calls. Very little connects end-to-end. A live smoke
test on 2026-08-04 found the buyer journey broken at every seam, and nearly every failure
traced to the same root cause:

**The UI thinks in `Product`s. The cart and order layers think in `ProductVariation`s.
Nothing translates between them.**

Concretely:

- `product/detail.html` never surfaces variations. `ProductController` puts only `product`
  and `category` in the model. A buyer has no way to choose what they are actually buying.
- `frontend/CartController.addToCart` papers over this by sending `product.getId()` as the
  cart item's `productSku` — handing a Product id to a layer that expects a
  ProductVariation id.
- The same confusion broke order creation: `OrderServiceImpl` called
  `retrieveProduct(item.getProductSku())`, hitting `GET /api/v1/product/{a-variation-id}`,
  which can never resolve.
- `Attribute` exists (name/value per variation) but nothing populates it, so there is no
  data to build a selector from even if the page wanted to.

Everything else — the DTO contract mismatch, the zero prices, the uncomputed totals — is
downstream of that seam or of nobody having driven the flow.

## Goals

1. A buyer can browse products, pick a variation via attribute selectors, add it to a
   cart, check out against a saved address, and see the resulting order.
2. The product/variation seam is closed once, in a way the rest of the system can rely on.
3. Guests can browse and fill a cart without an account.
4. The work is small enough to actually finish. Breadth is explicitly not the goal.

## Non-goals

Seller/admin UI (that is Slice 2, where RBAC is the point), tax, shipping, payment method
capture, guest checkout without login, re-pricing at checkout, the `Discount` model, and
any cleanup of unrelated generated CRUD.

## Architecture

No new services or infrastructure. Five existing pieces get connected:

```
product-service              frontend-service                 order-service
─────────────────            ────────────────                 ─────────────
Product                      product/detail.html              Cart (sessionId | userId)
 └ ProductVariation     →     [Size][Color] selectors    →     └ CartItem (productVariantId)
     └ Attribute              resolve combo → variationId           │
     └ Inventory              cart.html (server-side totals)        ↓ checkout
                              checkout.html (saved address)    Order → OrderItem
                                                                    ↓
                                                              payment-service
```

## Components

### 1. Catalog seeder (product-service, dev profile only)

A seeder that runs under the dev/local profile and creates a small realistic catalog:
3–5 products, each with 4–9 `ProductVariation`s spanning two attribute dimensions, each
variation carrying `Attribute` rows (`Size=M`, `Color=Red`) and an `Inventory` row. At
least one product must have two attribute dimensions so the multi-selector path is
exercised.

It must deliberately include at least one attribute combination with **no corresponding
variation**, and one variation with **zero stock**, so the "unavailable combo" and
"out of stock" paths have real data to exercise rather than being theoretical.

The seeder is also the fixture for integration tests, so tests and humans exercise the
same data.

Seeding must be idempotent — running it twice must not duplicate the catalog.

### 2. Variation-aware product page

**Backend.** The product detail path returns, in one payload: the product, its variations,
each variation's attributes, price, and available stock. One call, no N+1 per variation.

**Frontend.** `ProductController` puts variations and their attributes in the model.
`product/detail.html` renders one selector group per distinct attribute name (Size, Color,
…). Client-side logic resolves the selected combination to a variation id and:

- greys out combinations that map to no variation, or to a variation with zero stock
- updates the displayed price and stock from the resolved variation
- disables "Add to Cart" until a complete, available combination is selected

**The add-to-cart form submits a variation id.** This is the seam closing.

Visual treatment follows the general Amazon shape (image left, title/rating/price and
selectors right, add-to-cart in a panel). Layout specifics are deliberately not pinned
down here — it is frontend code and cheap to iterate.

### 3. Guest cart, claimed on login

Follows the standard anonymous-session pattern (see References).

- `Cart.sessionId` — already on the entity, with `findBySessionId` and
  `findBySessionIdAndStatus` already in the repository and service, currently unused —
  becomes the guest cart key. `userId` is null for a guest cart.
- The session cookie is created **lazily, on the first add-to-cart**, not on page load, so
  bots and bounced visitors do not create cart rows.
- Cookie is `HttpOnly`, `Secure`, `SameSite`.
- A guest is never a user. The anonymous identifier is never promoted into a real user id:
  that breaks for any returning user (their account already exists under a different id),
  requires rewriting every row written against the guest id, and makes a client-supplied
  identifier into a security principal — the exact shape hardened against in GH #17/#19.

**On login:**

- If the user has no active cart → **claim** it: set `userId`, clear `sessionId`. One
  update, no merging.
- If the user already has an active cart → **merge** line items into the user's existing
  cart. Items are matched by variation id. For a variation present in both carts,
  **retain the higher quantity, do not sum.** Summing produces the classic "I wanted one,
  I got two" failure.

After a merge, the guest cart row is marked `ABANDONED` rather than deleted, so the merge
is auditable and an accidental merge is recoverable. After a claim, no second row exists to
dispose of.

`CartStatus` already defines `ACTIVE`, `CHECKOUT_IN_PROGRESS`, `CONVERTED` and `ABANDONED`,
and **nothing in the codebase transitions to any of them except `ACTIVE`** — `clearCart`
explicitly leaves a checked-out cart `ACTIVE`. This slice should start using them
correctly: `ABANDONED` for a merged-away guest cart as above, and `CONVERTED` for the
user's cart once checkout successfully creates an order (replacing today's "clear the items
but leave it ACTIVE" behavior, which loses the fact that the cart was ever ordered).

In both cases the session cookie is cleared once the cart is associated with the user, so
a subsequent add-to-cart writes to the user cart rather than silently creating a new guest
cart.

Totals are recalculated server-side after a claim or merge.

### 4. Server-side pricing

- `unitPrice` is **snapshotted from the variation at add-to-cart time**, so a later price
  change does not silently mutate an existing cart.
- `lineTotal`, `subtotal` and `totalPrice` are **computed server-side on every cart
  mutation** and never read from the request. The current code trusts a client-supplied
  `lineTotal`, which is both why carts total zero (the frontend sends `BigDecimal.ZERO`)
  and a straightforward tampering vector.
- `totalPrice` = `subtotal` while tax and shipping are out of scope. The tax and shipping
  lines are **hidden in the UI** rather than displayed as `0.00`, so the page does not
  imply a calculation that does not exist.

This supersedes the interim `totalPrice` formula currently in `main` (commit `696eb4f`),
which was implemented without a decision on the pricing model. See GH #68.

### 5. Checkout → order

- Checkout requires authentication. A guest hitting checkout is redirected to login and
  returns with the cart intact (claimed per §3).
- Shipping address is selected from the user's saved addresses — reusing the profile and
  address work, which is finished and known-good.
- "Place order" calls the existing `OrderService.create`, which already handles inventory
  deduction with a ledger, payment via `PaymentClient`, and compensation on failure.
- On success the cart is cleared and transitioned to `CONVERTED` (see §3), so the same cart
  cannot be checked out twice and the ordered-from cart remains distinguishable from an
  empty active one.
- Order confirmation, then the existing order history and detail pages.

## Contract fixes this depends on

These are prerequisites, not incidental cleanup:

1. **Drop `@NonNull` from server-generated fields on inbound DTOs** — `id`, `createdAt`,
   `updatedAt` on `CartItemDto`, `OrderDto`, `OrderItemDto`. These are assigned by the
   server (`@GeneratedValue`, `@CreationTimestamp`). Requiring them on a create request is
   why `frontend-service`'s add-to-cart payload is rejected with 400 before reaching any
   service code (GH #68), and why `OrderServiceImpl.create` and `CartServiceImpl.addItem`
   currently need explicit `setId(null)` workarounds. Once the DTOs stop demanding them,
   those workarounds can be simplified.

2. **`CartItem.productVariantId` becomes authoritative.** The field already exists and is
   unused. `productSku` reverts to holding an actual SKU string, which is what its name
   says.

3. **Frontend must stop sending `product.getId()` as `productSku`.** Covered by §2.

## Data model

No schema changes beyond what exists. Specifically:

- `Attribute` is used for the first time — populated by the seeder, read by the product
  page. No change to its shape.
- `Cart.sessionId` is used for the first time. No change to its shape.
- `CartItem.productVariantId` is used for the first time. No change to its shape.

Note: `product_variation.created_at`/`updated_at` were NULL for every row inserted before
commit `6e33eec`; rows created before that fix stay NULL. The seeder creates fresh rows and
is unaffected.

## Error handling

| Case | Behavior |
|---|---|
| Attribute combo maps to no variation | Combination greyed out in the selector. Not an error. |
| Resolved variation has zero stock | Greyed out, "Out of stock" shown. Add to Cart disabled. |
| Stock ran out between add-to-cart and checkout | Checkout blocks, names the offending item, offers to adjust quantity or remove it. |
| Price changed between add-to-cart and checkout | MVP: honor the snapshot. Re-pricing is explicitly future work. |
| Guest reaches checkout | Redirect to login; cart preserved and claimed on return. |
| User has no saved address at checkout | Prompt to add one, returning to checkout afterwards. |
| Order creation fails downstream | Existing `OrderService.create` compensation applies (restore inventory, mark CANCELLED). Surface a clear failure; do not silently empty the cart. |

## Testing

Every seam that broke in the 2026-08-04 smoke test gets real coverage, using the seeder as
the shared fixture:

- Attribute combination → variation resolution, including a combo with no variation and a
  variation with zero stock.
- Add to cart snapshots the variation's current price; a later price change does not alter
  the existing cart item.
- Cart totals are computed server-side and ignore client-supplied `lineTotal`.
- Guest cart claim: user with no active cart gets the session cart, `sessionId` cleared.
- Guest cart merge: overlapping variation retains the higher quantity, not the sum.
- Checkout end-to-end: cart → order, inventory deducted, payment recorded, cart cleared and
  marked `CONVERTED`.
- Checkout blocked when stock is insufficient.
- The same cart cannot be checked out twice.

Integration tests use the existing Testcontainers setup. Note `BaseIntegrationTest`'s
shared-container lifecycle was fixed in commit `4439ff2`; new integration tests should
follow that singleton pattern rather than reintroducing `@Container` on a static field.

## Open decision for review

**Rename `OrderItem.productSku`.** It is a `UUID` holding a ProductVariation id. That name
is precisely what caused the `retrieveProduct(item.getProductSku())` bug. Renaming it to
`productVariationId` would remove a live footgun and materially improve readability for a
portfolio codebase.

**Proposed: out of scope for this slice**, filed as its own issue and done as a dedicated
commit. Rationale: it is a pure refactor that unblocks nothing here, it crosses
order-service, product-service and their tests, and this slice's whole premise is staying
small enough to finish. Bundling a cross-service rename into a feature slice is how scope
creeps.

Flagged explicitly because it is a reasonable call either way — say so if you would rather
it went in now.

## References

Guest-cart handling follows the established anonymous-session pattern rather than
identifier promotion:

- [Cart merge strategies — commercetools](https://docs.commercetools.com/learning-implement-carts-and-shopping-lists/manage-signups-and-signins/cart-merge-strategies)
  — names `MergeWithExistingCustomerCart` and `UseAsNewActiveCustomerCart`; matches line
  items by product/variant and retains the higher quantity.
- [Manage cart sessions and merges — commercetools](https://docs.commercetools.com/learning-implement-carts-and-shopping-lists/implement-carts/manage-cart-sessions-and-merges)
- [Implement guest checkout — commercetools](https://docs.commercetools.com/tutorials/anonymous-session)
  — create the anonymous session only once the visitor interacts with the cart.
- [Merging carts when a customer logs in — hybrismart](https://hybrismart.com/2019/02/24/merging-carts-when-a-customer-logs-in-problems-solutions-and-recommendations/)
  — duplicate-quantity and stock-availability pitfalls; the "two iPhone cases instead of
  one" case against summing.
