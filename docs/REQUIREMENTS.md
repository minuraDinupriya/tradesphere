# Requirements

Status: catalog/access (TRD-13), warehouse/receiving inventory (TRD-15), customers/LKR pricing (TRD-16), and pending orders/reservation (TRD-17) are merged. TRD-18 implements processing, cancellation, full fulfillment, and order-scoped audit. General security/business audit remains planned. Specified rules come from the project brief/current ticket; proposals and policies are labeled. Execution evidence belongs in the learning journal, not inferred from this list.

## MVP capabilities

- **Identity and access:** minimal session sign-in, one organization per user, catalog write permission, and a separate inventory-write permission. Authenticated same-organization users may read both. Existing users do not automatically acquire inventory-write access. Full roles/membership administration remain unresolved and out of scope.
- **Catalog:** TRD-13 supports creation, retrieval, editing, list/search/category/status filters, and bounded pagination. Approved fields: organization-unique SKU, name, optional description, category, unit of measure, and active status. TRD-16 adds separately authorized selling prices below; tax, images, product stock mutation, and deletion remain deferred. See [catalog API](api/PRODUCT_CATALOG.md).
- **Warehouses and receipts (TRD-15):** create/view/edit/search/filter/paginate organization-owned warehouses. Code is case-insensitively unique within the organization; fields are code, name, optional location, active status, and edit version. The scenario has three warehouses, but their identities are not invented or automatically seeded. Receive one active product into one active warehouse, using positive whole-unit quantities and optional reference/note. The request ID is unique per organization: identical normalized retries return the original receipt, changed payloads conflict. Movement and balance commit together; concurrent distinct receipts must not lose increments. Multi-line deliveries and corrections remain outside this ticket.
- **Inventory availability (TRD-15):** show on-hand, reserved, and generated available quantities by organization/warehouse/product for recorded balance rows. Untouched warehouse/product combinations are not expanded into zero rows. Receipts do not create reservations. Movement history/detail includes quantity, actor, timestamp, reference, and note. See [API limits and semantics](api/INVENTORY.md).
- **Customers (TRD-16):** approved name (required), optional contact person/email/phone/address, active status, UUID IDs; create/view/edit/search/status/pagination with organization isolation and independent customer-write permission. Names/contact details need not be unique. No deletion or portal.
- **Selling prices (TRD-16):** one organization-level LKR price per product across warehouses. New/existing products may start unpriced; explicit zero is allowed. Reject negative/invalid/extra-decimal amounts without rounding; use fixed-precision storage and decimal-string API/UI values. Price-write is independent of catalog/customer/inventory permissions. Version checks reject stale edits. See [API bounds and exclusions](api/CUSTOMERS_AND_PRICING.md).
- **Ordering (TRD-17):** create immutable pending orders for one active customer/warehouse with 1–100 distinct active, priced products; positive whole-unit quantities follow inventory's safe-integer limit. Snapshot product labels/unit/LKR price at creation and calculate exact totals on the server. Pending orders reserve nothing and need not have available stock. Confirm atomically reserves every line or none, rechecks activity without repricing, and handles concurrent/retried confirmation without double reservation. Order-write permission is independent of other write permissions. Creation also supports same-request-ID retry. See [order API](api/ORDERS.md) for bounds and semantics; TRD-18 extends lifecycle below.
- **Lifecycle (TRD-18):** process confirmed orders, cancel pending/confirmed orders, fulfill processing orders in full. State, reservation closure, balance deltas, and audit records share one transaction. Existing order-write access guards mutations; organization readers may view history. See the lifecycle below.
- **Audit (TRD-18):** order transitions and release/fulfillment movements record organization, actor ID, and timestamp, without duplicates on replay. Earlier creation/confirmation metadata is backfilled and explicitly labeled. General audit/security events, retention, and tamper-proof storage remain unresolved.

## Stock invariants

For each organization, warehouse, and product:

`available stock = on-hand stock - reserved stock`

- Confirming an order must reserve all required available inventory atomically. If any item cannot be reserved, the confirmation must fail without leaving partial reservations.
- Concurrent requests must not oversell inventory.
- Cancelling an eligible order must release its reservations exactly once, including under retries or concurrent requests.
- Fulfillment must consume the order's reservations and reduce physical on-hand stock consistently, as one atomic business operation.
- **Implemented balance constraints:** on-hand and reserved quantities remain non-negative, and reserved stock never exceeds on-hand stock. Quantity changes must preserve these constraints.
- **Approved receipt input rule:** positive whole units with quantities/balances at most 9,007,199,254,740,991 (JavaScript's exact integer limit). Fractional JSON inputs must be rejected, not rounded. Implemented orders use the same positive whole-unit bounds.

## Order lifecycle

TRD-18 extends TRD-17's `PENDING` state (`DRAFT` was only an earlier proposal). The developer approved these transitions:

- `PENDING` → `CONFIRMED` reserves every line, or `CANCELLED` changes no stock.
- `CONFIRMED` → `PROCESSING` keeps stock reserved, or `CANCELLED` releases all reservations without changing physical stock.
- `PROCESSING` → `FULFILLED` consumes all reservations and decreases on-hand/reserved equally. Cancellation and direct confirmed fulfillment are disallowed.
- `CANCELLED` and `FULFILLED` are terminal. Headers/items/warehouse/prices remain immutable; no amendment or reopening exists.
- Implementation policy: a previously recorded action returns 200 with `replayed:true` and the current order, including after further progress, without new effects or state regression. An unrecorded invalid action returns 409.
- Existing reservations can be released/consumed after customer/product/warehouse deactivation; rechecking new-order eligibility would strand already allocated stock. Cancellation/fulfillment never reserve fresh stock.
- State history and stock movements commit with the business change. A later-item failure rolls back the entire operation. See [API](api/ORDERS.md) and [decision 0006](decisions/0006-order-lifecycle.md).

## Organization isolation and security

- Tenant-owned records must belong to an organization and be accessible only within an authorized organization scope.
- Backend authorization must guard every relevant operation. UI controls alone are insufficient.
- References between records must not permit cross-organization access, including order/customer/warehouse/product relationships.
- Client-provided organization identifiers must not establish trust; the backend must verify the user's authority for the organization.
- Reads, writes, list/search endpoints, audit access, and identifier-based lookups must respect the same isolation boundary.
- Secrets and real personal/customer data must not be committed or used for demo fixtures.

## Acceptance expectations

These scenarios define required evidence when the associated features are implemented:

- A receipt increases only the intended warehouse/product balance in the intended organization.
- Identical concurrent/repeated receipt requests increase stock once; competing distinct receipts retain both increments. A failed or overflowing receipt leaves no movement or balance effect.
- Inactive products/warehouses reject new receipts. Warehouse edits detect stale versions. Catalog-only editors cannot mutate warehouses or receive stock.
- An order with sufficient stock reserves every item; available stock falls while on-hand stock remains unchanged.
- Order snapshots/totals remain unchanged after catalog price/name/unit changes; unpriced products reject creation while zero-priced products are valid.
- Repeated/concurrent creation with one request ID returns one original order; mismatched payloads conflict. Repeated/concurrent confirmation of one order reserves once, and a simulated database failure rolls back stock, ledger, and state together.
- An order with one insufficient item reserves nothing and does not become confirmed.
- Competing confirmations against limited stock succeed only within the available quantity; committed balances remain valid.
- Repeated and concurrent eligible cancellations release each reservation once.
- Fulfillment reduces on-hand and reserved stock by the same fulfilled quantities; it cannot consume the same reservation twice.
- Competing cancellation and fulfillment result in one valid terminal outcome and consistent balances.
- Invalid quantities, illegal state transitions, unauthorized actions, and cross-organization references are rejected without side effects.
- A user from one organization cannot read or mutate another organization's records, even using known identifiers.

Use real PostgreSQL integration tests for transaction and concurrency guarantees. Unit tests alone cannot establish database locking behavior. See [workflow](DEVELOPMENT_WORKFLOW.md).

## Explicit exclusions

The initial MVP has no backorders, partial fulfillment, real payments, or multi-warehouse allocation within a single order. Advanced infrastructure is deferred as described in the [roadmap](ROADMAP.md).

## Decisions needed before affected features

TRD-13 resolves product/access in [decision 0002](decisions/0002-product-catalog-and-session-access.md), TRD-15 receiving in [decision 0003](decisions/0003-warehouse-inventory.md), and TRD-16 customer/LKR prices in [decision 0004](decisions/0004-customers-and-sales-pricing.md). TRD-17 covers snapshots/reservation in [decision 0005](decisions/0005-order-reservation.md); TRD-18 covers lifecycle/audit in [decision 0006](decisions/0006-order-lifecycle.md). Future decisions include quote expiry/amendment, taxes, full membership/roles, receipt corrections, returns, and general audit contents. Do not silently implement open questions.
