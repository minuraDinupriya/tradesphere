# Requirements

Status: initial requirements baseline. All capabilities are **planned**, not implemented. Specified business rules below come from the project brief; proposed details and open questions are labeled separately.

## MVP capabilities

- **Identity and access:** authenticate users and enforce backend permissions and organization scope. Exact roles and authentication flow remain unresolved.
- **Catalog:** manage products needed for stock and order workflows. Product fields, identifiers, and archival behavior remain to be agreed.
- **Warehouses and receipts:** represent the three warehouses and record stock receipts that increase the appropriate warehouse's on-hand balance. Receipt correction behavior is unresolved.
- **Inventory availability:** show on-hand, reserved, and available quantities by organization, warehouse, and product.
- **Customers:** manage the customer records needed to place B2B orders. Exact fields and ownership details remain unresolved.
- **Ordering:** create multi-item orders for one warehouse, confirm them by reserving stock, cancel eligible orders, and fulfill orders consistently.
- **Audit:** record important security and business changes. Event fields, retention, and visibility are unresolved.

## Stock invariants

For each organization, warehouse, and product:

`available stock = on-hand stock - reserved stock`

- Confirming an order must reserve all required available inventory atomically. If any item cannot be reserved, the confirmation must fail without leaving partial reservations.
- Concurrent requests must not oversell inventory.
- Cancelling an eligible order must release its reservations exactly once, including under retries or concurrent requests.
- Fulfillment must consume the order's reservations and reduce physical on-hand stock consistently, as one atomic business operation.
- **Proposed derived constraints:** on-hand and reserved quantities remain non-negative, and reserved stock never exceeds on-hand stock. Quantity changes must preserve these constraints.
- **Proposed input rule:** order and receipt quantities must be positive whole units, consistent with the [quantity assumption](PROJECT_CONTEXT.md).

## Proposed order lifecycle

Candidate states are `DRAFT`, `CONFIRMED`, `CANCELLED`, and `FULFILLED`. Final names and transition rules must be agreed before implementation.

- A draft has no stock reservation; confirmation reserves every item in the selected warehouse.
- A confirmed order can be fulfilled in full or cancelled; both are terminal actions in this proposed MVP lifecycle.
- Draft cancellation may be allowed and releases no inventory. Exact cancellation eligibility remains unresolved.
- Confirmed items and warehouse should be immutable unless a separately designed atomic amendment workflow is introduced; the proposed MVP omits that workflow.
- Repeated or competing confirmation, cancellation, and fulfillment requests must never repeat their stock effects. Whether an already completed request returns success or a conflict is an API decision still to be made.

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
- An order with sufficient stock reserves every item; available stock falls while on-hand stock remains unchanged.
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

Resolve user roles/membership and authentication; product/customer fields; pricing, currency, rounding and tax scope; receipt corrections and stock adjustments; cancellation eligibility; retry response semantics; and audit contents. Do not silently turn these open questions into implemented business rules.
