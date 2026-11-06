# 0003 — Warehouse and inventory operations

Date: 2026-11-06 (Asia/Colombo). Status: **implemented on the TRD-15 feature branch; PR review/merge pending**. Developer-approved business scope: separate inventory-write permission (no silent grant to existing users); one active product per receipt into one active warehouse; positive whole units; optional reference/note; request-ID retries. [TRD-15](https://minuraranaweera.atlassian.net/browse/TRD-15). Implementation choices below are refinable engineering decisions, not invented external approvals.

## Context and choice

The first stock slice must connect warehouse management, receiving, balances, and traceable receipt history without losing concurrent increments or double-counting a retried delivery. Order reservation/fulfillment/procurement and corrections are excluded.

- Retain JDBC and immutable records from TRD-13. `WarehouseService` owns optimistic edits; `InventoryService` owns receipt transactions. A public `CatalogProductAccess.lockActiveStatus` operation checks/locks catalog eligibility inside the caller's transaction (`MANDATORY`). No Inventory write goes through Catalog's repository.
- V4 creates warehouses, composite-key balances, and receipt movements. Organization-aware foreign keys prevent foreign warehouse/product/actor relationships. Controllers always derive organization/actor from `OrganizationUser`; shared Problem Details handling moves to `com.tradesphere.api` so Inventory does not depend on catalog HTTP advice.
- `inventory:write` is independent of `catalog:write`. V4 defaults existing users to false. Explicit initial empty-database bootstrap creates a first editor with both flags, while databases containing users remain untouched. Permission changes require a new session; no user administration/session-revocation system is introduced.
- A unique `(organization_id, request_id)` movement key arbitrates concurrent retries. `ON CONFLICT DO NOTHING RETURNING` waits for a competing insert, then a read-committed lookup compares normalized payloads. Matching requests replay the original movement; changed payloads conflict.
- Receipt movement insertion and balance increment occur in one Spring transaction. `INSERT ... ON CONFLICT DO UPDATE` adds to the committed row atomically; it does not read stock into Java and overwrite a potentially stale total. Overflow throws a runtime business exception, rolling back the newly inserted movement. There is no global warehouse lock: compatible `FOR SHARE` warehouse/product locks prevent concurrent deactivation while balances contend only for the affected pair.
- `available` is a PostgreSQL generated column (`on_hand - reserved`); checks enforce nonnegative values and `reserved <= on_hand`. Use `bigint`/Java `long`, capped at JavaScript's maximum safe integer to keep JSON/TypeScript quantities exact. Fractional JSON-to-integer coercion is disabled. This limit is a representation bound, not a real warehouse capacity.
- Paginated read projections join current product/warehouse/user labels, always on organization-aware keys. This read-only schema coupling avoids N+1 calls and duplicate label storage in a small monolith; future schema changes must account for it. Cross-module mutations still go through explicit application operations. Movement IDs, actor, quantity, and time are retained; labels are not historical snapshots.
- React uses existing layout/form/feedback styles, a small abortable data hook, and the existing HTTP/CSRF client. The receiving page retains an immutable submitted payload after uncertain responses and offers same-ID retry. No new dependency, broker, cache, or state platform is needed.

## Alternatives and consequences

A read-then-write stock update can lose increments; a transaction alone does not fix that race. Optimistic balance versions would need retry logic; explicit row locking would need a safe first-row creation path. The PostgreSQL upsert handles both creation and increment in one statement. Receipt uniqueness still needs its own key because atomic increments alone would faithfully apply a duplicate twice.

An independent audit write could survive a failed stock change or be lost after stock commits. The same-transaction movement avoids those inconsistencies. It remains application append-only, not a cryptographically verifiable or database-role-enforced audit system. A general Audit module remains future work.

Decimal/string quantities could support fractional units or all `bigint` values, but would require explicit unit/rounding/API design. Whole-unit safe integers fit the approved MVP. Multi-line receipts would introduce multi-row atomicity and lock-order decisions and remain deferred.

Warehouse/product selectors are deliberately bounded (100 warehouses, 20 product matches with search), suitable for the stated three-warehouse scenario. Offset pagination may move between requests; list/count share a snapshot only within one response. Retry state does not survive leaving/reloading the receiving page; users must inspect movement history before creating a replacement delivery after losing that state. Persistent recovery and immediate session permission revocation are not implemented.

## Visual reference and UX

The supplied [Figma Make reference](https://www.figma.com/make/YaoKSVrzcg2BBRuCKAt9OV/Build-TradeSphere-SaaS-Platform) is inspiration under the repository's design policy. TRD-13 established the white sidebar, light canvas, purple actions, and form/table styles. For TRD-15, the connector exposed source links without preview content; screenshot retrieval is unsupported for Make and browser preview attempts timed out. Inventory-specific reference fidelity was therefore **not verified**; no unavailable screen details were invented.

Reuse the existing visual system, accessible labels/linked field errors/status feedback, honest empty states, and contained horizontal table scrolling on narrow screens. Include only warehouse management, receiving, balances, and receipt history; no placeholder analytics, order actions, transfers, or procurement controls. Exact receipt retry recovery feedback and separate permission gating serve correctness/usability. Local browser screenshots are actual implementation evidence, not Figma exports.

## Verification and remaining scope

Local `.\mvnw.cmd -B -ntp verify` passed 8 unit and 23 PostgreSQL/API integration tests (none skipped), including concurrent distinct/identical receipts, rollback, fractional input rejection, eligibility, independent permissions, CSRF, and tenant/FK isolation. `npm run lint`, `npm run test` (22 component tests), `npm run build`, and `npm run test:e2e` (3 real Edge browser tests) passed. The browser test deliberately drops a committed receipt's response and verifies safe replay. Exact commands/directories are in the [command log](../COMMAND_LOG.md); hosted CI evidence will be recorded in the [journal](../LEARNING_JOURNAL.md).

Order concurrency/overselling, corrections, transfers, generic audit, load/failover tests, Firefox/Safari, comprehensive accessibility, production deployment, and full identity lifecycle remain unverified or unimplemented. See the [API](../api/INVENTORY.md) and [requirements](../REQUIREMENTS.md).
