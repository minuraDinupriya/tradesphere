# Test strategy

For each substantial Jira feature, derive important scenarios from its requirements and acceptance criteria before implementation. Briefly explain the planned coverage, considering positive, negative, boundary, authorization, and failure cases; then proceed with coding and testing without unnecessary approval checkpoints.

## Approach

- Write appropriate automated tests alongside production code. Prefer a failing-test-first approach for well-defined business rules where practical; do not claim TDD was used when it was not.
- Unit-test meaningful business behavior without a database or HTTP server.
- Use real PostgreSQL integration tests for migrations, constraints, persistence, transactions, isolation, and concurrency. An in-memory database is not evidence of PostgreSQL correctness.
- Exercise API validation, error contracts, permissions, tenant boundaries, and relevant failure paths through the actual request boundary.
- Use frontend component tests for user interactions, forms, navigation, and loading/empty/error/success states.
- Use automated browser E2E tests for critical complete workflows across the real frontend, backend, and PostgreSQL. Keep fixtures fictional and isolated from development data.
- Add reusable regression tests for important defects; reproduce the defect before the fix when practical.

## Evidence and sustainability

Run relevant tests before opening a PR. Record exact commands, actual results, skipped/unavailable checks, and uncovered risks in the PR and meaningful learning-journal entry. Never invent test results or label an unexecuted test as passing. Repeat/broaden validation when changes, failures, or risk justify it.

Routine tests belong to the current feature's Definition of Done, not separate Jira tickets. Keep scenario notes concise; avoid large repetitive manual test-case documents. A passing suite supports the behavior it covers, not unspecified production guarantees.

## TRD-13 scenario focus

Planned coverage (not results):

- Create, retrieve, update, search, category/status filtering, stable pagination, and persisted normalized data.
- Invalid/oversized fields, required values, SKU uniqueness within an organization, literal search wildcards, pagination bounds, and stale-version updates.
- Anonymous denial, read-only users, CSRF protection, sign-in/sign-out, and cross-organization list/detail/update/category isolation.
- PostgreSQL uniqueness enforcement and competing updates without lost edits.
- Frontend form submission/validation, permissions, request failures/retry, empty/loading/success states, and browser sign-in → create → search → view → edit → sign-out.

## TRD-15 scenario focus

Requirements-derived scenarios implemented alongside this slice (results are in the journal):

- Warehouse create/view/edit/search/status/pagination, organization-unique codes, stale-version conflicts, and field validation.
- Receipt persistence, correct warehouse/product increments, generated availability, actor/reference/note history, and active eligibility.
- Zero/negative/fractional/unsafe quantities, invalid identifiers, overflow rollback, and mismatched request-ID payloads.
- Concurrent distinct receipts retain both increments; concurrent identical request IDs create one receipt/increment. Tests coordinate requests and inspect committed database state.
- Anonymous/CSRF/permission denial, catalog-only versus inventory-only writers, foreign identifiers, tenant-scoped lists/details, and composite foreign keys.
- Component form/filter/retry/permission/error/empty interactions. Real browser workflow: sign in, create/edit warehouse, receive, inspect inventory/history, and retry after the server commits but its response is deliberately dropped.

Order reservation/fulfillment tests belong to their future features; TRD-15 results do not prove overselling protection for unimplemented orders. Browser coverage is Chromium-family only, not exhaustive accessibility, load, or disaster-recovery testing.

## TRD-16 scenario focus

- Customer create/detail/edit/search/status/pagination, normalization, invalid email/field lengths, duplicate names allowed, literal search wildcards, and stale updates.
- Unpriced versus zero, exact fractional/max LKR amounts, negative/extra-decimal/exponent/JSON-number rejection, and fixed-precision PostgreSQL persistence.
- Separate customer-only/price-only writers, legacy permission defaults, anonymous/CSRF denial, known foreign IDs/list isolation, and catalog payload price tampering.
- Concurrent price edits from the same version have one winner; ordinary catalog edits preserve the price.
- Component creation/validation/filter/retry/empty states and stale customer/price reload; API submission/display preserve decimal strings. Browser create/edit/search customer, set/reject/edit/reload product price, responsive screenshots, and existing catalog/inventory regressions.

Actual results belong in the journal/command log. This is not evidence of unimplemented orders, general price history, multi-currency, or production hardening.

## TRD-17 scenario focus

- Valid single/multi-item pending creation, 1–100 distinct lines, quantities/nulls/fractions/bounds, active/missing/foreign customer/warehouse/products, unpriced versus zero, exact large totals, and immutable price/label snapshots.
- Order writer versus unrelated writers/readers, anonymous/CSRF denial, scoped lists/detail/actions, and server-owned organization/prices/state.
- One insufficient later line or absent balance leaves the entire order pending with no reservations. Simulated PostgreSQL trigger failure after an earlier reservation attempt must roll back all balances/ledger/status; the test-only trigger is scoped to its fixture organization and removed afterward.
- Coordinated concurrent distinct orders compete for stock without overselling; same-order confirmation reserves once; reverse product input order follows a stable lock protocol; receipt/confirmation contention preserves physical/reserved totals. Inspect committed results, not only HTTP statuses.
- Creation replay compares canonical quantities and never reprices; changed payload conflicts. Confirmation replay succeeds even after later deactivation, retaining actor/time.
- Component create/list/detail interactions, invalid/duplicate lines, loading/empty/error/success states, uncertain creation payload retention, confirmation retry, and read-only controls. Real browser multi-item creation/confirmation ties snapshots to inventory availability, alongside existing regression workflows.

Unit tests support local rules; only real PostgreSQL tests establish database contention/rollback evidence. TRD-17 results alone do not establish lifecycle behavior; TRD-18 coverage follows below. Exhaustive deadlock/load/failover guarantees remain outside these results.

## TRD-18 scenario focus

- Exercise all five states against four actions, including successful replays without state regression. Approved rules forbid cancellation after processing and require processing before fulfillment.
- Pending cancellation changes no stock; confirmed cancellation releases once; full fulfillment consumes reservations and subtracts physical/reserved equally. Snapshots and receipt history remain unchanged; deactivated master records cannot strand existing allocations.
- Coordinate duplicate cancel/fulfill, processing versus cancel, cancel versus fulfill, confirmation versus cancel/fulfill, overlapping distinct orders, and receipt contention. Inspect committed balances, reservation closure, actors/times, audit counts, and terminal states.
- Inject a fixture-scoped failure on a later movement insertion for cancellation and fulfillment; prove earlier attempts occurred and all business writes rolled back. Reject inconsistent reservation quantities without partial effects.
- API tenant/read-only/anonymous/CSRF denial and direct SQL graph/closure/delta/actor constraints. Upgrade a uniquely named disposable PostgreSQL database from V6 with existing orders/reservations to V7 and verify metadata/backfill/balances remain correct. The configured test database owner needs create/drop database privileges; local Compose/CI use disposable owners. Never run this suite against production.
- Component tests cover all status filters, permission/terminal controls, dialogs/dismissal, stale-state reload, uncertain same-action retry, and readable history/error recovery. The real-browser lifecycle journey covers Escape/focus restoration, both cancellations, processing, fulfillment after a deliberately lost committed response, persisted audit/stock, and mobile overflow, alongside all prior journeys.

No production incident is simulated as history; injected failures exist only in isolated tests. Results are recorded in the journal/command log. Browser coverage remains Chromium-family, with no exhaustive accessibility, stress, or disaster-recovery claim.

See [workflow](../DEVELOPMENT_WORKFLOW.md) for review rules, [README](../../README.md) for runnable commands, and [learning journal](../LEARNING_JOURNAL.md) for executed verification evidence.
