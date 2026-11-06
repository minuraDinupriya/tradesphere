# 0006 — Transactional order lifecycle and scoped audit

Date: 2026-11-06 (Asia/Colombo). Status: implemented on the TRD-18 branch; verification/PR status belongs in the journal. [TRD-18](https://minuraranaweera.atlassian.net/browse/TRD-18) was read through Jira; TRD-17 was verified merged at `a3cb509`. This extends [decision 0005](0005-order-reservation.md); its original cancellation/fulfillment exclusions remain historical TRD-17 scope.

## Context and approved policy

Complete the single-warehouse order workflow without changing immutable order prices/items or receipt semantics. The developer explicitly approved **no cancellation after processing starts** and **processing required before fulfillment**. Graph: pending → confirmed/cancelled; confirmed → processing/cancelled; processing → fulfilled. Fulfilled/cancelled are terminal. Shipping, payments, returns, partial fulfillment, backorders, amendments, and deployment remain excluded.

## Choice

- Keep the modular monolith, JDBC, existing order-write permission, session organization authority, and CSRF. `OrderTransitions` centralizes new transitions; existing confirmation retains its service and reservation path.
- `OrderLifecycleService` locks the scoped order first. Check the recorded target action before rejecting its current state: replay returns the current order without regressing it or changing original actors/times. The order/action is the retry identity; a separate key adds no value for a once-only acyclic action.
- Confirmed cancellation and processing fulfillment call Inventory inside the same transaction (`MANDATORY`). Lock the order's reservation rows; compare the full active item/quantity set with immutable order items. Then close reservations and update shared balances in PostgreSQL UUID order. Conditional writes and database checks reinforce the locked protocol. Any failure escapes and rolls everything back.
- Release subtracts reserved only; consumption subtracts on-hand and reserved equally. Pending cancellation and starting processing have no balance effect. Do not recheck master-record activity when closing existing allocations: that would strand stock after deactivation.
- V7 retains closed reservation records and adds release/fulfillment movements, unique per order/product, alongside order state history unique per target state. Keep TRD-15's receipt-only `stock_movements`/API unchanged. Ordering owns transitions, Inventory owns inventory effects; no event bus/general Audit subsystem is necessary.
- Audit records commit with the action. Composite tenant/order/warehouse/actor foreign keys and operation-shape checks prevent invalid references/deltas. Backfill only existing creation/confirmation metadata, explicitly marked reconstructed. PostgreSQL transaction timestamps are not commit timestamps; display transitions in causal state order. IDs identify actors; usernames are current labels.
- React reuses established forms/tables/styles and adds native modal confirmation with safe initial focus, Escape/Go back dismissal, and focus restoration. Show only permitted state actions, but rely on backend enforcement. Uncertain mutation retries keep the same order/action; explicit reload resolves stale controls. Designs remain visual references; no new Figma fidelity is claimed.

## Alternatives and consequences

An unlocked check then update permits duplicate stock effects. Optimistic versions or serializable transactions would need retry orchestration without improving this small, once-only graph; the existing order lock is simpler. Deleting reservations would discard useful closure evidence. Mixing negative release rows into the positive physical-receipt ledger would change established semantics; a narrow second ledger keeps the distinction explicit. A general event-sourcing or asynchronous audit platform adds operational cost before a demonstrated need.

The graph is acyclic, so a unique target event doubles as a replay marker; reopening/repeating transitions would require a new operation identity and schema design. Locks are held through stock/audit commit, so contention can delay a caller. Stable balance ordering reduces cycles for current workflows; no arbitrary deadlock/load/failover guarantee is claimed. Audit writes are append-only through these application paths, not tamper-proof against database owners. General retention and security failure logging remain future work.

## Evidence

Tests cover the full state/action matrix; cancellation/fulfillment retries and races; overlapping orders/receipts; simulated later-movement failure with full rollback; tenant/permission/CSRF boundaries; direct constraint rejection; and upgrade from V6 with existing reservations. Component/browser journeys cover consequential dialogs, terminal controls, audit, stock consistency, and a deliberately dropped committed fulfillment response. Actual commands/results and uncovered risks belong in the [journal](../LEARNING_JOURNAL.md), [command log](../COMMAND_LOG.md), and PR, not inferred from this scenario list.
