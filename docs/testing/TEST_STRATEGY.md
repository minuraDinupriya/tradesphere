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

See [workflow](../DEVELOPMENT_WORKFLOW.md) for review rules, [README](../../README.md) for runnable commands, and [learning journal](../LEARNING_JOURNAL.md) for executed verification evidence.
