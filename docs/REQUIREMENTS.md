# Requirements

Status: catalog/minimal session access (TRD-13) and warehouse/receiving inventory (TRD-15) are implemented; customers, order reservations/fulfillment, and general audit remain **planned**. Specified rules come from the project brief and the developer's approved ticket scope; proposals and open questions are labeled separately. Execution evidence belongs in the learning journal, not inferred from this requirements list.

## MVP capabilities

- **Identity and access:** minimal session sign-in, one organization per user, catalog write permission, and a separate inventory-write permission. Authenticated same-organization users may read both. Existing users do not automatically acquire inventory-write access. Full roles/membership administration remain unresolved and out of scope.
- **Catalog:** TRD-13 supports creation, retrieval, editing, list/search/category/status filters, and bounded pagination. Approved fields: organization-unique SKU, name, optional description, category, unit of measure, and active status. Pricing, tax, images, stock quantities, and deletion are deferred. See [API rules and limits](api/PRODUCT_CATALOG.md).
- **Warehouses and receipts (TRD-15):** create/view/edit/search/filter/paginate organization-owned warehouses. Code is case-insensitively unique within the organization; fields are code, name, optional location, active status, and edit version. The scenario has three warehouses, but their identities are not invented or automatically seeded. Receive one active product into one active warehouse, using positive whole-unit quantities and optional reference/note. The request ID is unique per organization: identical normalized retries return the original receipt, changed payloads conflict. Movement and balance commit together; concurrent distinct receipts must not lose increments. Multi-line deliveries and corrections remain outside this ticket.
- **Inventory availability (TRD-15):** show on-hand, reserved, and generated available quantities by organization/warehouse/product for recorded balance rows. Untouched warehouse/product combinations are not expanded into zero rows. Receipts do not create reservations. Movement history/detail includes quantity, actor, timestamp, reference, and note. See [API limits and semantics](api/INVENTORY.md).
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
- **Implemented balance constraints:** on-hand and reserved quantities remain non-negative, and reserved stock never exceeds on-hand stock. Quantity changes must preserve these constraints.
- **Approved receipt input rule:** positive whole units with quantities/balances at most 9,007,199,254,740,991 (JavaScript's exact integer limit). Fractional JSON inputs must be rejected, not rounded. Whole units remain the working assumption for future orders, which require their own validation design.

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
- Identical concurrent/repeated receipt requests increase stock once; competing distinct receipts retain both increments. A failed or overflowing receipt leaves no movement or balance effect.
- Inactive products/warehouses reject new receipts. Warehouse edits detect stale versions. Catalog-only editors cannot mutate warehouses or receive stock.
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

TRD-13 resolves the initial product/access design in [decision 0002](decisions/0002-product-catalog-and-session-access.md). TRD-15 resolves separate inventory-write access, one-product whole-unit receipts, eligibility, and request-ID retries in [decision 0003](decisions/0003-warehouse-inventory.md). Before affected later features, resolve full membership/role administration, customer fields, pricing/currency/rounding/tax, receipt corrections/adjustments, cancellation eligibility, order retry semantics, and general audit contents. Do not silently turn open questions into implemented rules.
