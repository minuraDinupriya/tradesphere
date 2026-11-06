# 0002: TRD-13 catalog and minimal session access

Date: 2026-11-06 (Asia/Colombo). Status: accepted for this implementation within the developer-authorized scope; merge approval remains separate.

## Context and approved scope

TRD-6 was merged before starting TRD-13. In this chat the developer approved minimal Spring Security session sign-in, organization-bound users, catalog read/write permissions, and product SKU/name/description/category/unit/active fields. Prices, taxes, stock, images, deletion, registration, password recovery, and user administration are excluded.

## Choices and tradeoffs

- Retain JDBC rather than adding JPA. `Product` is an immutable record; a repository owns explicit, parameterized PostgreSQL queries, a service owns normalization/transactions, and controllers validate API DTOs. SQL is visible for learning and tenant review; mapping is more manual than an ORM.
- Derive the organization from the authenticated principal. Every repository operation requires its organization ID; API payloads do not establish that authority. Use one organization per user and globally unique, case-insensitive usernames so sign-in does not need a client-selected organization. Full multi-membership switching is deferred.
- Use Spring Security's server-side sessions, BCrypt hashes, CSRF protection, and session-fixation protection. JWT would add token lifecycle/storage concerns without a current need. The UI uses the same origin via the development proxy, HttpOnly cookies, and fresh CSRF tokens after login/logout. Production TLS/secure cookies and abuse protection remain separate work.
- Provide an explicit, disabled-by-default bootstrap only for an empty user database. It creates one organization/editor from local environment configuration; it never resets existing users and adds no account-administration endpoints. Tests provision generated fixtures separately.
- Enforce SKU uniqueness in PostgreSQL per organization, not only in an application precheck. Uppercase SKU normalization makes matching predictable. Category and unit labels remain free text; a managed taxonomy is unnecessary for the approved scope.
- Require an update version and atomically compare/increment it in SQL. A stale request gets 409 rather than silently overwriting another person's edit. This is optimistic concurrency: appropriate for occasional catalog editing; inventory reservation still needs its own future transaction design.
- Use bounded server-side pagination with deterministic sorting. Repeatable-read gives list rows/count one snapshot; do not claim it freezes pages across separate requests. Simple substring search is adequate initially; no search engine or PostgreSQL extension is justified by current evidence.
- Use native React controls, feature folders, a small fetch/CSRF client, and shared field/feedback components. Abort superseded reads; retain entered values on failed saves. No extra state-management or UI framework is required.

## Design reference review and deviations

The [Figma Make reference](https://www.figma.com/make/YaoKSVrzcg2BBRuCKAt9OV/Build-TradeSphere-SaaS-Platform) was inspected through its live Products and Add Product previews. The connector returned source-resource links but does not support Make screenshots/sessions; the actual preview was visible in the browser. No generated React source was copied and no exported Stitch assets were supplied.

Preserved the white sidebar, purple accent, light canvas, compact product table, search/category/status toolbar, grouped form cards, and status badges. Excluded price/tax inputs, deletion, stock warnings, fake counts, notifications, unused navigation, the workspace switcher, and a decorative stepper. These exceed TRD-13 or imply behavior that does not exist.

Added visible labels, field-linked errors, a skip link, a real detail page/read-only mode, retry and explicit stale-edit recovery, responsive layout, and a horizontal-table hint. Used a project-native text brand rather than claiming fidelity to unprovided logo assets. Requirements, accessibility, usability, and maintainability govern the implementation; this is not a pixel-perfect reproduction.

## Evidence and limitations

Unit tests, real PostgreSQL/API tests (including tenant boundaries and competing writes), frontend interaction tests, and browser workflows support the implemented scope. See the [test strategy](../testing/TEST_STRATEGY.md), [learning journal](../LEARNING_JOURNAL.md), and [command log](../COMMAND_LOG.md) for actual commands/results.

No claim of production readiness, inventory correctness, full identity administration, or exhaustive accessibility certification is made. Offset pagination may shift under edits between requests; substring search needs reassessment if measured data volume warrants it.
