# Orders, reservation, and lifecycle — TRD-17 / TRD-18

Uses the existing [session/CSRF flow](PRODUCT_CATALOG.md). Every query and referenced record is scoped to the principal's organization. Authenticated organization users may read orders/history; only `orders:write` may create, confirm, process, cancel, or fulfill. Catalog, inventory, customer, or price write permissions do not imply order write access. TRD-18 adds no permission flag or automatic user grants.

## Create a pending order

`POST /api/v1/orders`

```json
{
  "requestId":"11111111-1111-4111-8111-111111111111",
  "customerId":"22222222-2222-4222-8222-222222222222",
  "warehouseId":"33333333-3333-4333-8333-333333333333",
  "items":[
    {"productId":"44444444-4444-4444-8444-444444444444","quantity":3},
    {"productId":"55555555-5555-4555-8555-555555555555","quantity":2}
  ]
}
```

Illustrative IDs must be replaced with real same-organization records. Requirements:

- One active customer and one active warehouse; 1–100 distinct active products.
- Each product must have an explicitly set selling price. Zero is valid; unset is not.
- Quantities are positive whole units, at most 9,007,199,254,740,991 (existing inventory representation limit). Fractions, nulls, missing IDs, duplicates, and invalid UUIDs are rejected.
- The server snapshots product ID/SKU/name/unit and the current unit price, plus customer/warehouse labels. It calculates line totals and the complete LKR total; client prices/totals/organization/status cannot establish authority.
- Pending creation reserves no inventory and may succeed with unavailable stock. Stock is checked when confirming.

Response: **201** new or **200** creation replay, `Location: /api/v1/orders/{id}`, body `{ "order": { ...detail }, "replayed": false|true }`. Generate one request UUID per intended order and retain it across uncertain responses. Organization/request uniqueness prevents duplicate creation; identical customer/warehouse/product quantities replay the same order without repricing. Line order is ignored. Reusing the ID for another payload is 409. Replay may show the order's current confirmed status. No key expiration is implemented.

Pending orders cannot be edited/deleted in this slice. After uncertain creation, the UI must retry the original payload/ID. Retry state held by the page does not survive navigation/reload; reconcile the order list before creating a replacement after losing that state. This follows the existing receipt approach rather than introducing offline drafts.

## Read/list

- `GET /api/v1/orders/{id}` → detail; missing/foreign ID → 404.
- `GET /api/v1/orders?q=&status=PENDING&page=0&size=20` → `{items,totalElements,page,size,totalPages}`.

Optional q (max 100) matches order UUID/customer snapshot/warehouse snapshot, using case-insensitive literal substring search. Status is empty or one of `PENDING`, `CONFIRMED`, `PROCESSING`, `FULFILLED`, `CANCELLED`. Page is zero-based/nonnegative; size 1–100, default 20. Sorting is newest creation/ID first. Rows/count share a repeatable-read snapshot within one response; later pages can shift under changes.

Summary fields: `id`, `requestId`, `customerId`, `customerName`, `warehouseId`, `warehouseName`, `warehouseCode`, `status`, `currency`, decimal-string `totalAmount`, `createdAt`, nullable `confirmedAt`, `createdBy`, nullable `confirmedBy`. Detail adds `items` containing `productId`, `sku`, `productName`, `unitOfMeasure`, integer `quantity`, and decimal-string `unitPrice`/`lineTotal`. Items are returned in deterministic product-ID order. Actor IDs/timestamps record actual creation/confirmation; replay does not replace them.

Prices and totals are immutable snapshots. Later catalog/price/contact edits do not reprice an existing order. Money uses Java BigDecimal/PostgreSQL numeric and JSON strings; order totals use `numeric(32,2)` because multiplying safe-integer quantities by bounded prices and summing up to 100 lines can exceed the product price range and JavaScript exact numbers. React must not convert these strings to Number for money arithmetic.

## Confirm

`POST /api/v1/orders/{id}/confirm` (no body; session/CSRF required) → **200**, `{order,replayed}`.

The server locks the organization-scoped order row. Already-confirmed requests return the existing confirmation before checking mutable eligibility, with no extra reservations. A pending confirmation rechecks active warehouse/customer/products, preserves its original prices, and conditionally reserves all quantities within one database transaction. Insufficient/missing balance on any line returns **409** and commits no reservations or order transition. On-hand stock is unchanged; reserved increases and available decreases. Zero-priced products still require stock.

Competing orders serialize on affected stock rows and cannot reserve beyond availability. Duplicate/concurrent confirmation of one order reserves once. Reservation records identify organization/order/warehouse/product/quantity/actor/time, separate from physical receipt history. In TRD-18 a recorded confirmation replays even after processing, cancellation of a previously confirmed order, or fulfillment; it returns the current order and never re-reserves. A never-confirmed cancelled order returns 409.

Unexpected database failure rolls back all stock/ledger/status changes and returns generic 500. A lost response does not establish failure: retry the same order confirmation or reload its detail. The same order ID is the confirmation idempotency identity; no separate confirmation key or automatic blind retry loop is needed. Database contention/deadlock/connection failures are not guaranteed never to occur; do not equate the tested lock protocol with a universal deadlock guarantee for future features.

## Process, cancel, and fulfill (TRD-18)

All actions are bodyless POSTs with session, CSRF, and order-write permission. New actions and replays return **200** `{order,replayed}`.

- `POST /api/v1/orders/{id}/process`: `CONFIRMED` → `PROCESSING`; stock remains reserved. The developer approved disallowing cancellation after processing starts.
- `POST /api/v1/orders/{id}/cancel`: `PENDING` → `CANCELLED` without stock effects, or `CONFIRMED` → `CANCELLED` releasing every reservation exactly once. Physical on-hand stays unchanged; availability rises. Processing/fulfilled orders reject cancellation with 409.
- `POST /api/v1/orders/{id}/fulfill`: `PROCESSING` → `FULFILLED` only. All reservation quantities are consumed; on-hand and reserved decrease equally, leaving availability unchanged. Direct confirmed fulfillment is disallowed by the developer-approved rule.

Terminal orders cannot be reopened or changed. A previously recorded action is a safe replay, even after later progress: e.g. processing retry after fulfillment returns the fulfilled order, not processing. Replay retains original actor/time and creates no extra movement/history. Rejected unrecorded actions return 409 with the current-state concern and reload guidance. Cancel after fulfillment and fulfill after cancellation never apply.

The order ID plus action identifies the operation; no new request key is required. Under the order lock, Inventory validates the complete active reservation set against immutable items and closes it under deterministic product locks/conditional balance updates. Missing or inconsistent reservations return 409 with no committed changes. Unexpected failure returns generic 500 and rolls back all state, balance, reservation, movement, and audit updates. Existing allocations may close after source records are deactivated. No new stock is allocated during release/fulfillment.

Use the UI confirmation dialog before consequential actions. After a network/500 uncertainty, retry the same action/order or explicitly reload; never assume a dropped response means rollback. There is no blind automatic retry loop. Reloading after a stale-state 409 retrieves current permitted controls.

## Scoped lifecycle history (TRD-18)

`GET /api/v1/orders/{id}/history` → **200** `{transitions:[],movements:[]}`. Missing/foreign order returns 404. Any signed-in organization reader may access it. Both arrays share one repeatable-read transaction snapshot.

- Transitions: `id`, nullable `fromStatus`, `toStatus`, `actorId`, `actorUsername`, `occurredAt`, `backfilled`. Includes creation, confirmation, processing, cancellation, and fulfillment as applicable, ordered in lifecycle sequence. Each target state can be recorded once per order.
- Movements: `id`, `orderId`, `warehouseId`, `productId`, snapshot `sku`/`productName`, `kind` (`RELEASE` or `FULFILL`), positive `quantity`, signed `onHandDelta`/`reservedDelta`, `actorId`, current `actorUsername`, `occurredAt`.
- Release deltas are `0/-quantity`; fulfillment deltas are `-quantity/-quantity`. One closure movement per order/product. Original receipt history remains exclusively incoming physical receipts; lifecycle movements are available on this order history endpoint/UI.

V7 reconstructs creation/confirmation facts from existing header metadata and labels them `backfilled:true`; new transitions use false. Timestamps use PostgreSQL transaction time, not exact commit time. Causal lifecycle sorting avoids misordering overlapping requests whose transactions started in another order. Usernames are current display labels; actor IDs are retained facts. History is application-maintained, not a tamper-proof audit store. No general audit API, retention policy, or security-denial events are implemented.

## Errors, setup, and exclusions

Shared `application/problem+json`: 400 invalid fields/types/bounds/duplicate products; 401 no session; 403 permission/CSRF; 404 absent/foreign order/source; 409 inactive/unpriced creation sources, request mismatch, or insufficient/ineligible confirmation; 500 unexpected failure without internal traces. Field errors are provided where Bean Validation can identify them. Order-input business errors also explain the correction in `detail`.

V6 gives existing users `can_write_orders=false`. Explicit fresh-empty-database bootstrap creates the first editor with order write access; existing accounts are unchanged. Suggested owner-only setup (not executed on existing development users):

```sql
UPDATE tradesphere.users SET can_write_orders = true
WHERE id = 'replace-with-actual-user-uuid'::uuid
  AND organization_id = 'replace-with-actual-organization-uuid'::uuid
RETURNING id, organization_id, username, can_write_orders;
```

Verify the intended row, then sign out/in. Immediate session permission revocation remains unimplemented. No role-administration UI is introduced. Taxes, discounts, invoices, payments, multi-currency, returns, shipping integrations, partial orders, backorders, reservation expiry, and deployment are excluded. See [decision 0005](../decisions/0005-order-reservation.md), [decision 0006](../decisions/0006-order-lifecycle.md), [test strategy](../testing/TEST_STRATEGY.md), and [learning journal](../LEARNING_JOURNAL.md) for actual evidence.
