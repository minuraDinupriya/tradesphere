# 0005 — Pending orders and atomic reservation

Date: 2026-11-06 (Asia/Colombo). Status: implemented on the TRD-17 branch; validation/review/merge status belongs in the journal. [TRD-17](https://minuraranaweera.atlassian.net/browse/TRD-17) was retrieved from Jira, with TRD-15/TRD-16 dependencies verified on latest main (`50ff58d`). Planning prose still described future backlog, but the actual ticket is In Progress in Sprint 3 and the developer explicitly authorized implementation.

## Scope and policy

Create immutable pending multi-item orders for one customer/warehouse, snapshot prices at creation, confirm all stock or none, prevent concurrent overselling, and make retries safe. Cancellation, fulfillment, shipping, payments, partial allocation, and backorders are excluded. `PENDING` supersedes the earlier proposed `DRAFT` name for this implemented slice; future terminal states remain proposals.

Implementation policies use the existing conventions: independent order-write permission defaulting false for existing users, active customer/warehouse/products at creation and confirmation, reject duplicate products, require set price (zero allowed), existing whole-unit safe-integer limit, and 1–100 lines to bound request/transaction work. No editing pending lines or repricing confirmation is introduced. These are documented engineering policies within the requested scope, not invented prior external approvals.

## Choice and execution

- Keep JDBC and one PostgreSQL transaction. Ordering owns header/items/snapshots and the lifecycle; Catalog exposes a public transactional eligibility/price snapshot operation; Inventory exposes warehouse eligibility and reservation operations. Ordering does not mutate another module's tables/repositories.
- V6 stores immutable unit-price/product labels on order items; PostgreSQL generates line totals and the server computes the header total using BigDecimal. Prices retain numeric(14,2); order totals use numeric(32,2), sufficient for 100 maximum-price/maximum-quantity lines. All money remains strings across JSON/React. Current catalog edits cannot alter a historical quote.
- Order creation uses an organization/request UUID unique key and canonical product/quantity comparison. An identical retry returns the original order without new snapshots. This also protects creation after an uncertain response, beyond the explicitly required confirmation idempotency.
- Confirmation locks the order `FOR UPDATE` first. A confirmed order replays immediately, retaining original actor/time even if source records were deactivated later. A pending operation then acquires warehouse/customer share locks, **all product share locks in UUID order**, then balance writes in the same UUID order. UUID text ordering matches PostgreSQL UUID order, including high-bit UUIDs; Java UUID's signed natural comparison is deliberately avoided.
- Each balance update atomically adds reserved quantity only when current available stock is sufficient. Missing rows count as unavailable. The transaction writes a distinct reservation ledger and the confirmed header. Any business/database failure rolls back every earlier update/ledger write. Physical on-hand and receipt-only stock_movements are untouched.
- The order lock provides retry serialization; conditional balance updates prevent overselling among distinct orders. These mechanisms solve separate problems. Unique reservation keys and composite tenant/order/item/warehouse/balance/actor foreign keys reinforce integrity. `MANDATORY` propagation rejects cross-module reservation/eligibility calls without the caller's transaction.

## Alternatives and costs

An unlocked check then Java overwrite can oversell even inside a transaction. Explicit balance `SELECT FOR UPDATE` plus recheck is valid, but conditional updates express the rule in one statement and handle missing balances without inventing stock rows. Optimistic balance versions would require retry orchestration. Serializable transactions could work but add serialization retry cases; no broker/cache/distributed transaction is required.

Lock all eligibility products before writing any balance. Interleaving product A/balance A/product B could create cycles with existing warehouse → product → balance receipt updates. Sorted balance acquisition reduces multi-order deadlocks; this is a protocol for current operations, not a claim that every future lock combination is deadlock-free. An unexpected failure/timeout can still occur and callers can retry/reload safely.

Snapshotting at creation makes pending totals stable and prevents later price changes from silently changing a quote. It also means a pending order may retain an old price indefinitely: expiry/reapproval/amendment policy is future work. Confirmation rechecks activity but does not require the current price to match the saved price. Unit labels are snapshots; no unit conversion is implemented.

Separate reservations preserve the meaning of existing receipt history. General audit/retention, database-role-enforced immutability, reservation expiry/release, and downstream lifecycle are not added. Existing authenticated readers see organization-scoped orders; only order writers create/confirm, regardless of other write flags. Session authority refresh follows existing sign-out/in rules.

## UI and verification

Reuse the established layout/forms/tables, accessible labels, literal search, honest empty/error/loading states, snapshot totals, and separate pending/confirmed feedback. No decorative unavailable order actions or invented Figma details. Uncertain creation retains its payload/request ID while mounted; confirmation retries use the same order ID. Navigation/reload loses a creation draft, requiring reconciliation before replacement, consistent with the existing receipt limitation.

Real PostgreSQL tests must establish contention, same-order retries, insufficient later-line rollback, source/tenant isolation, price snapshots, and simulated database failure rollback; mocks alone cannot prove these. An independent review within the agent work is not external human approval. See [API](../api/ORDERS.md), [test strategy](../testing/TEST_STRATEGY.md), [command log](../COMMAND_LOG.md), and [journal](../LEARNING_JOURNAL.md) for executed outcomes and remaining risks.
