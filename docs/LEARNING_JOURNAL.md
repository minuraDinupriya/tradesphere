# Engineering learning journal

Completed entries are chronological, oldest first, grouped by meaningful Jira issue or engineering milestone. Follow the automatic maintenance and accuracy rules in [MENTORING_GUIDELINES.md](MENTORING_GUIDELINES.md). Add new entries using the same nine bold fields below; update an existing milestone when correcting it.

## 2026-11-06 — TRD-6: Engineering foundation

**Date and Jira ticket**

2026-11-06 (Asia/Colombo). TRD-6 — TradeSphere Engineering Foundation. The issue key was supplied by the developer; its Jira URL is unknown. Branch: `chore/TRD-6-engineering-foundation`. Commits: minimal README bootstrap `3a9adfb`, backend foundation `4da3b6a`, frontend foundation `ccb0dc2`, CI/documentation `c5fefcc`. [PR #1](https://github.com/minuraDinupriya/tradesphere/pull/1) is open and unmerged.

**What We Built**

A runnable Java 21/Spring Boot backend with health reporting, PostgreSQL in Docker Compose, an initial Flyway schema migration, and a React/TypeScript preview with reusable layout and routing. Added backend integration tests, frontend checks/tests, and a GitHub Actions CI workflow. Business features, authentication, tenant enforcement, Stitch implementation, and production deployment remain planned and unimplemented.

**Concepts I Should Understand**

- **Spring Boot and dependency injection:** Boot configures components from dependencies and properties. Spring creates the `JdbcTemplate` used by `FoundationIT` and injects it; the test does not manually assemble a database connection pool. There is no business service dependency graph yet.
- **Maven:** the POM describes dependencies/plugins and the wrapper selects Maven. `verify` compiles, packages, and runs Failsafe integration tests; `test` alone does not run `*IT` tests. A successful skipped-test build does not establish test correctness.
- **React and TypeScript:** components produce UI from data; routing selects which page appears inside the shared layout. TypeScript catches type errors at build time, not every runtime failure.
- **Docker and PostgreSQL:** a container isolates the database process, while a named volume keeps data beyond that container. A health check signals readiness; it is more reliable than sleeping for an arbitrary duration.
- **Flyway and schemas:** migrations change the database in order and record history/checksums. A schema is a namespace. PostgreSQL's search path can change which unqualified table name is found.
- **CI versus CD:** CI repeats validation on GitHub; CD would deliver the app to an environment. This milestone configures CI only.

**Real-World Analogy**

Think of preparing a hardware store before opening: the backend is the service counter, React is the storefront, PostgreSQL is the stock ledger, and Flyway is the numbered set of ledger-format changes. Compose brings the ledger service online; CI checks the setup in another workspace. The analogy stops at infrastructure: stock, sales, staff access, and reservations are not implemented yet.

**How It Works Internally**

1. [compose.yaml](../compose.yaml) starts PostgreSQL with environment configuration, a loopback port, readiness checks, and persistent storage.
2. [TradeSphereApplication](../backend/src/main/java/com/tradesphere/TradeSphereApplication.java) starts Boot. [application.properties](../backend/src/main/resources/application.properties) loads local configuration; Boot builds the data source and Flyway applies [V1](../backend/src/main/resources/db/migration/V1__create_application_schema.sql) before startup completes. Flyway history is explicitly anchored in `public`.
3. Actuator serves health over HTTP. [FoundationIT](../backend/src/test/java/com/tradesphere/FoundationIT.java) starts the app on a random port, checks live HTTP behavior, and queries the actual PostgreSQL schema/history via injected `JdbcTemplate`.
4. [main.tsx](../frontend/src/main.tsx) mounts React and the browser router. [App.tsx](../frontend/src/App.tsx) chooses the route; [AppLayout](../frontend/src/app/AppLayout.tsx) supplies navigation and renders its `Outlet`; the [historical DashboardPage at ccb0dc2](https://github.com/minuraDinupriya/tradesphere/blob/ccb0dc2/frontend/src/features/dashboard/DashboardPage.tsx) rendered the clearly labeled preview (replaced by real catalog screens in TRD-13). Unknown routes offered a return link.
5. [CI](../.github/workflows/ci.yml) runs backend/Compose and frontend jobs independently. It generates a disposable database password, runs validation, and cleans up its own CI volume.

**Engineering Decisions**

Keep one backend and feature-oriented UI folders. Use JDBC without speculative business repositories/JPA, host-run application servers, and Compose for PostgreSQL. Integration tests use the same PostgreSQL setup rather than H2; Testcontainers would automate test lifecycle further but adds tooling we do not need yet. Keep only health exposed and defer Spring Security until authentication. Detailed rationale and alternatives are in [decision 0001](decisions/0001-engineering-foundation.md).

**Problems and Debugging**

- Initializr generated `4.1.2.RELEASE`, which Maven could not resolve. Changed the parent coordinate to the published `4.1.2`; compilation and later full verification succeeded.
- The first integration run passed two tests but failed an exact health-body assertion because Boot included health group names. Adjusted the test to verify `UP` and absence of component details instead of requiring an unrelated response shape.
- On restart, Flyway looked for history in `tradesphere` instead of `public`, attempted V1 again, and failed because the schema already existed. Logs showed the changed history location; SQL confirmed `search_path = "$user", public` and `current_schema() = tradesphere` for the matching database user. Set `spring.flyway.default-schema=public`, qualified the test's history query, and removed only the empty history table created by the failed local run. Full verification and a packaged-application restart then passed without reapplying V1.
- The default shell Java was 11 and system Maven used Java 20. Selected the existing JDK 21 via process-local `JAVA_HOME`/`PATH`; no machine-wide settings changed. Docker's user-profile CLI also needed a process-local PATH entry.

Actual verification (from repository root unless stated):

- `docker version`, `docker compose version`: Docker Desktop's Linux engine and Compose were reachable through the installed CLI.
- `docker compose config --quiet`, `docker compose up -d --wait --wait-timeout 120`, `docker compose ps`: passed; PostgreSQL 18.6 became healthy.
- `docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version();"'`: connected to PostgreSQL 18.6.
- From `backend/`, `.\mvnw.cmd -B -ntp validate` initially failed on the generated version; `.\mvnw.cmd -B -ntp package -DskipTests` then built successfully, with tests explicitly skipped.
- From `backend/`, `.\mvnw.cmd -B -ntp verify`: first failed the health assertion, then exposed the history-schema error on rerun; after both fixes, **3 tests passed, 0 failed, 0 skipped**, and the jar was built.
- From `backend/`, `& "$env:JAVA_HOME\bin\java.exe" -jar target/tradesphere-0.0.1-SNAPSHOT.jar`: initially failed on the history-schema issue; after the fix, started on port 8080 and found V1 already applied. HTTP health returned 200 with `UP`.
- From `frontend/`, `npm install --package-lock-only`, `npm ci`, `npm run lint`, `npm run test`, `npm run build`: passed; **3 frontend tests passed**, including route recovery. TypeScript and the production bundle build passed.
- From `frontend/`, `npm run dev`: started at port 5173. `Invoke-WebRequest` to `/` and `/src/main.tsx` returned 200. A browser accessibility snapshot and screenshot confirmed the rendered preview; mobile layout and Stitch fidelity were not verified.
- `.\.local\actionlint\actionlint.exe -color .github/workflows/ci.yml`: passed using temporary ignored actionlint 1.7.12. PowerShell relative-link checks (63 links after this entry) and `git diff --check` passed.
- [GitHub-hosted CI run 42207823947](https://github.com/minuraDinupriya/tradesphere/actions/runs/42207823947) for implementation commit `c5fefcc`: both backend/PostgreSQL and frontend jobs completed successfully. The Ubuntu runner started a fresh Compose database, ran `./mvnw -B -ntp verify`, then cleaned up; frontend `npm ci`, lint, tests, and build also passed. This establishes hosted CI execution, not production deployment or user acceptance.

Non-fatal Maven repository-metadata and Mockito agent warnings appeared during successful verification. No benchmarks, inventory concurrency tests, authentication tests, or production deployment were performed; those behaviors do not exist in this milestone.

**Interview Questions**

1. **What does dependency injection change?** Spring supplies managed dependencies such as `JdbcTemplate`, keeping consumers separate from connection-pool construction and configuration.
2. **Why test against PostgreSQL rather than only an in-memory database?** Real integration tests exercise PostgreSQL SQL, migration history, and connection behavior; a substitute may hide differences such as schema lookup.
3. **Why did the migration work once and fail on restart?** Creating a schema matching the database user changed implicit schema lookup. Flyway then found a different history location; explicitly setting the history schema made restarts consistent.
4. **What survives when a Docker container is removed?** Data in a separately retained named volume can survive; data only in the container's writable layer does not. Removing the volume loses its data.
5. **Why is a passing build different from a verified feature?** Compilation catches some errors, but behavior needs executed tests and runtime checks. Here, skipping tests built a jar while later integration tests still found faults.

**What I Should Practise**

1. Trace a route from `main.tsx` through `AppLayout` to `DashboardPage`, then add a temporary local route and a navigation test. Treat this as an exercise, not completed project scope.
2. In a disposable PostgreSQL database, inspect `search_path` and compare a qualified query with an unqualified one. Explain how a schema matching the user name changes lookup; do not alter the project database for the exercise.

## 2026-11-06 — TRD-13: End-to-end product catalog

**Date and Jira ticket**

2026-11-06 (Asia/Colombo). TRD-13, with branch `feature/TRD-13-product-catalog`, started from merged TRD-6 (`5facf9a`). Jira URL is not known. Commits: backend `40482c6`, frontend/browser validation `ae236b8`, documentation `20b64f4`. Update during TRD-15: [PR #2](https://github.com/minuraDinupriya/tradesphere/pull/2) was merged by the developer; current base is `7d7b048`. Its original review-pending notes below describe the earlier report, not current PR status.

**What We Built**

Organization-scoped product create/list/search/filter/pagination/detail/edit across PostgreSQL, Spring Boot, and React. The developer explicitly approved minimal session sign-in and catalog read/write permissions for this slice, plus SKU/name/description/category/unit/active fields. There are no price, stock, deletion, registration, or account-administration features. The [design decision](decisions/0002-product-catalog-and-session-access.md) records the inspected Figma reference and scope/UX deviations.

**Concepts I Should Understand**

- **Authentication, authorization, and tenancy:** sign-in identifies the user; authorities determine allowed operations; the user's server-owned organization scopes every catalog query. Hiding an Edit link is a convenience, not the security boundary.
- **Sessions and CSRF:** an HttpOnly cookie identifies a server-side session. A separate CSRF token helps establish that a browser mutation originated from this application's flow. It is not a replacement for permissions. The client fetches a fresh token after authentication rotates it.
- **Database constraints versus prechecks:** two requests can both pass an application uniqueness check. PostgreSQL's organization/SKU unique index makes the final decision atomically.
- **Optimistic concurrency:** the update SQL compares the version read by the editor and increments it in the same statement. A stale edit gets 409 so it cannot silently erase somebody else's change.
- **Validation and DTOs:** request records describe editable inputs; response records omit organization authority and password data. Bean Validation rejects bad requests; database checks reinforce integrity.
- **React request state:** forms retain values on failure, report field-linked errors, and disable pending submissions. Superseded reads are aborted. API communication is separate from presentation; the app does not pretend unimplemented controls work.
- **Test layers:** unit tests isolate rules, PostgreSQL/API tests exercise real integrity/security, and browser tests connect the actual UI, server, and database. See the [earlier foundation concepts](#2026-11-06--trd-6-engineering-foundation) for Maven, Docker, and basic dependency injection.

**Real-World Analogy**

A hardware-store clerk's staff badge establishes which distributor they work for and whether they may edit its catalog. Every ledger lookup includes that distributor, even if the clerk knows another store's product number. A product's version is like a numbered revision slip: if another clerk has already submitted a newer slip, an old edit must be reconciled rather than overwrite it.

**How It Works Internally**

1. [V2](../backend/src/main/resources/db/migration/V2__create_organization_users.sql) adds organizations/users; [V3](../backend/src/main/resources/db/migration/V3__create_products.sql) adds products, constraints, and indexes. No credentials are seeded by migrations.
2. [SecurityConfiguration](../backend/src/main/java/com/tradesphere/identity/SecurityConfiguration.java) enforces permissions/CSRF. [OrganizationUserDetailsService](../backend/src/main/java/com/tradesphere/identity/OrganizationUserDetailsService.java) loads the organization and BCrypt hash; [SessionController](../backend/src/main/java/com/tradesphere/identity/SessionController.java) returns safe session/CSRF DTOs. Bootstrap is explicit and only acts on an empty user database.
3. [ProductController](../backend/src/main/java/com/tradesphere/catalog/ProductController.java) validates input and derives organization scope from the principal. [ProductService](../backend/src/main/java/com/tradesphere/catalog/ProductService.java) normalizes values and owns transactions; [ProductRepository](../backend/src/main/java/com/tradesphere/catalog/ProductRepository.java) binds SQL parameters, applies tenant predicates, and performs version-checked updates. [ApiExceptionHandler](../backend/src/main/java/com/tradesphere/api/ApiExceptionHandler.java) maps expected failures to useful Problem Details (moved from Catalog to shared API in TRD-15).
4. [AuthProvider](../frontend/src/features/identity/AuthProvider.tsx) tracks sign-in. The [HTTP client](../frontend/src/shared/api/http.ts) supplies cookies/CSRF tokens; [productApi](../frontend/src/features/catalog/productApi.ts) separates API calls from [list](../frontend/src/features/catalog/ProductListPage.tsx), [form](../frontend/src/features/catalog/ProductFormPage.tsx), and [detail](../frontend/src/features/catalog/ProductDetailsPage.tsx) components.

**Engineering Decisions**

Retain JDBC/immutable records rather than adding an ORM for this slice. Use one organization per user and minimal read/write authorities rather than inventing a full role hierarchy. Use session cookies rather than adding JWT lifecycle/storage complexity. Bound pagination and literal substring search avoid unnecessary search infrastructure. Category/unit remain text labels. Use repeatable-read for a consistent list/count snapshot, not a promise that separate pages never shift under edits. See [decision 0002](decisions/0002-product-catalog-and-session-access.md) for alternatives and limitations.

**Problems and Debugging**

- Windows held the existing jar open in the earlier backend process; compilation passed but repackaging failed. Inspected and stopped that specific process, then full verification passed. A later clean npm install similarly found a native binding held by the earlier Vite server; process identity was checked before stopping it. These were tool/process lifecycle issues, not product-domain bugs.
- The first frontend build caught unsupported `exact` options on Testing Library role queries; component tests alone had not caught the TypeScript issue. Removed the unsupported options and retained the meaningful interactions. Including E2E files in type checking then exposed missing DOM types; added them and rebuilt successfully.
- Lint identified unnecessary synchronous state resets in effects. Moved resets to the initiating filter/retry/reload events, and keyed product routes so a new identifier gets fresh state. Existing component tests cover loading, retries, and stale-edit recovery; no failing product incident is invented.
- PostgreSQL/API tests deliberately exercise stale versions, simultaneous duplicate creation, literal search wildcards, and tenant tampering as reusable prevention/regression scenarios. They were not observed production incidents. TDD was not used for all of this implementation; scenario planning and tests accompanied the slice, with final execution evidence below.

Verification actually executed: `.\mvnw.cmd -B -ntp verify` from `backend/` passed **4 unit + 12 integration tests**; `npm run lint`, `npm run test`, and `npm run build` from `frontend/` passed after the documented fixes (**11 component tests**). `npm run test:e2e` passed **2 real browser tests** using installed Edge and a separate PostgreSQL test database; this included desktop/mobile screenshots and a no-page-overflow check. Actionlint passed static CI validation. See [COMMAND_LOG.md](COMMAND_LOG.md) for exact commands, flags, directories, repeated-run results, and current hosted CI status.

[Hosted CI run 42215790048](https://github.com/minuraDinupriya/tradesphere/actions/runs/42215790048) for implementation/documentation commit `20b64f4` completed successfully: frontend install/lint/tests/build/documentation checks, backend/PostgreSQL tests, real Chromium workflow, and disposable database cleanup all passed. This is separate evidence from local Edge verification. Developer review and merge approval remain outstanding.

Uncovered risks: Firefox/Safari were not run; this is not exhaustive accessibility testing or production hardening. No benchmarks, inventory transactions, production deployment, or external reviewer approval are claimed.

**Interview Questions**

1. **Why include organization scope in every query?** Authentication alone does not constrain a product ID to that user's organization; the predicate prevents cross-tenant access even with a known foreign ID.
2. **Why is a database unique index needed when the UI checks a SKU?** Concurrent requests can race between a check and an insert. The index enforces uniqueness at commit/statement execution regardless of the caller.
3. **How does optimistic concurrency prevent lost updates?** `UPDATE ... WHERE version = :expectedVersion` allows only a request with the current version to change the row; one competing editor receives a conflict instead of overwriting the winner.
4. **Why keep CSRF protection with cookie sessions?** Browsers attach cookies automatically, so a malicious site could attempt a mutation using them. A token bound to the legitimate session adds a check; it does not grant authorization.
5. **Why use component tests and E2E tests?** Components give focused interaction/failure feedback; E2E tests catch integration failures across routing, CSRF, cookies, HTTP, and real persistence. Neither alone proves all behavior.

**What I Should Practise**

1. In your local fictional catalog, open the same product in two tabs, save one, and explain why the other receives 409. Inspect the request version and the SQL predicate before trying to resolve the conflict.
2. Add a focused test for a new validation boundary in a temporary exercise branch, predict its failure, and implement the smallest fix. Explain whether it belongs in unit, API/PostgreSQL, or browser coverage. This is practice, not an additional approved feature.

## 2026-11-06 — TRD-15: Warehouse and inventory operations

**Date and Jira ticket**

2026-11-06 (Asia/Colombo). [TRD-15](https://minuraranaweera.atlassian.net/browse/TRD-15), branch `feature/TRD-15-warehouse-inventory`, based on merged TRD-13 (`7d7b048`). Commits: backend `baafab3`, frontend/tests `91ef466`. Implementation and local verification are recorded below; PR review/merge and demonstrated developer understanding remain separate outstanding states. The earlier mentoring-policy draft remains in its named stash, separate from this deliverable.

**What We Built**

Organization-scoped warehouse create/view/edit/search/filter/pagination, receiving one active product into an active warehouse, on-hand/reserved/available views, and receipt history/detail. Inventory write access is separate from catalog access. Existing accounts do not silently gain it. A repeated receipt request returns the original result without another stock increase; concurrent distinct receipts retain both increments. Orders, reservations, corrections, transfers, and procurement were not implemented. See the [API](api/INVENTORY.md).

**Concepts I Should Understand**

- **Atomicity versus concurrency:** a transaction makes movement and stock commit/rollback together; an atomic SQL increment also prevents competing requests from overwriting each other's totals. These solve different problems.
- **Idempotency:** an organization-scoped request ID identifies one intended delivery. A database unique constraint arbitrates races, while payload comparison prevents reusing the ID for a different delivery.
- **Database-generated values:** PostgreSQL computes available stock from on-hand minus reserved and checks their relationship, avoiding three independently editable quantities.
- **Defense in depth:** server permissions guard actions, organization predicates scope reads/writes, and composite foreign keys reject cross-organization relationships. None substitutes for the others. The catalog/session basics are covered in the earlier entry.
- **Uncertain outcomes:** a missing HTTP response does not mean the transaction failed. Retain the original submitted payload for retry rather than create a fresh delivery ID.

**Real-World Analogy**

A warehouse clerk stamps a numbered delivery slip and updates the stock ledger together. If the confirmation call drops, showing the same slip finds the existing stamp; it does not unload the same truck twice. Two different slips add their quantities to the ledger. The stamp (request uniqueness), combined ledger operation (transaction), and safe addition (atomic update) each have a different job.

**How It Works Internally**

1. [StockReceivingPage](../frontend/src/features/inventory/StockReceivingPage.tsx) selects warehouse/product and creates a UUID for the receipt. The existing HTTP client sends the session and CSRF token. After an uncertain response, fields stay locked and retry reuses the exact payload.
2. [InventoryController](../backend/src/main/java/com/tradesphere/inventory/InventoryController.java) obtains organization/user IDs from the principal. [InventoryService](../backend/src/main/java/com/tradesphere/inventory/InventoryService.java) first checks for a replay, locks warehouse/product eligibility, inserts a movement, then increments the balance in the same transaction.
3. [InventoryRepository](../backend/src/main/java/com/tradesphere/inventory/InventoryRepository.java) uses `ON CONFLICT` for request uniqueness and balance upsert. [V4](../backend/src/main/resources/db/migration/V4__create_warehouse_inventory.sql) supplies composite foreign keys, generated availability, and integrity checks. Overflow throws an exception and rolls back the inserted movement.
4. [WarehouseService](../backend/src/main/java/com/tradesphere/inventory/WarehouseService.java) uses version-checked edits; [CatalogProductAccess](../backend/src/main/java/com/tradesphere/catalog/CatalogProductAccess.java) exposes the eligibility lock across the module boundary. Read projections join current labels without mutating other modules.

**Engineering Decisions**

Keep JDBC, one PostgreSQL transaction, and existing React styles; no broker/cache/new framework. Use atomic upsert instead of Java read/overwrite, and request uniqueness instead of trusting a disabled button. Shared eligibility locks allow receipts to run concurrently while blocking deactivation. Cap whole-unit quantities at JavaScript's exact integer limit rather than silently round large/fractional numbers. [Decision 0003](decisions/0003-warehouse-inventory.md) records alternatives and visual-reference limitations. Current labels are not historical snapshots; retry state is lost on navigation/reload, so history must be checked before entering a replacement delivery.

**Problems and Debugging**

- Real PostgreSQL tests failed on nullable UUID filters: `IS NULL` alone left a bound parameter's type unknown. Explicit `CAST(... AS uuid)` fixed that query path; the integration suite then passed.
- A negative API test expected fractional quantity 1.5 to fail, but Jackson coerced it to integer 1. Disabled float-to-integer coercion; the same test now rejects it with 400 and no receipt effect.
- TypeScript rejected a filter interface passed as an indexed record; passing an object spread satisfied the helper contract without an unsafe cast. Rebuilt successfully.
- Self-review found that a later 403 after an uncertain response reset the receipt ID. Added a focused regression test first: it failed because the quantity input became enabled. Preserving the original payload through subsequent errors made the test pass. This was a local defect, not a production incident; TDD was used for this regression, not claimed for the whole feature.

Actual verification: backend `.\mvnw.cmd -B -ntp verify` passed **8 unit + 23 PostgreSQL/API tests**, none skipped. Frontend `npm run lint`, `npm run test` (**22 component tests**), and `npm run build` passed. `npm run test:e2e` passed **3 real Edge tests**, including a server-committed receipt with a deliberately dropped response and same-ID recovery (25 total units, not 30). `npm run test -- src/features/inventory/Inventory.test.tsx` failed for the new regression before the fix, then passed all 11 inventory tests. Exact commands/directories and other checks are in [COMMAND_LOG.md](COMMAND_LOG.md).

Uncovered: order lifecycle correctness, Firefox/Safari, exhaustive accessibility/load/failover testing, immediate permission revocation, durable retry drafts, and production deployment. Inventory-specific Figma previews could not be inspected; actual browser screenshots were reviewed using the established visual system. No external review or learner understanding is claimed.

**Interview Questions**

1. **Why isn't `@Transactional` enough to prevent lost stock updates?** Two transactions can read the same old value and both overwrite it; SQL must lock, compare a version, or increment the current row atomically. This feature uses an atomic upsert.
2. **How do retries avoid duplicate receipts?** A unique organization/request key permits one insert; a retry compares its normalized payload and returns the original movement. A different payload gets 409.
3. **What if a movement inserts successfully but the balance overflows?** The runtime exception rolls back the transaction, so neither the movement nor the balance change commits. PostgreSQL integration tests verify this.
4. **Why add organization to foreign keys when IDs are UUIDs?** UUID uniqueness does not establish authorized ownership. Composite references enforce that related records belong to the same organization.
5. **What can a client infer from a network timeout?** Only that it did not receive confirmation; the server may have committed. Retry the same operation ID or reconcile history rather than assume failure.

**What I Should Practise**

1. Trace the receipt's request ID from React through the controller/service to both SQL statements. Explain in your own words which mechanism prevents partial writes, lost increments, and duplicate deliveries.
2. On a disposable exercise branch, predict and test what happens when two receipts of 7 and 11 units arrive together, then when both use one request ID with different quantities. Use database assertions, not only HTTP status codes. This is suggested practice, not work already completed by the developer.
