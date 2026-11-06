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

2026-11-06 (Asia/Colombo). [TRD-15](https://minuraranaweera.atlassian.net/browse/TRD-15), branch `feature/TRD-15-warehouse-inventory`, based on merged TRD-13 (`7d7b048`). Commits: backend `baafab3`, frontend/tests `91ef466`, documentation `191d610`, browser diagnostics `6ee82be`. Update during TRD-16: [PR #3](https://github.com/minuraDinupriya/tradesphere/pull/3) is merged on main as `3f49db4`; earlier review-pending notes describe its original report. Demonstrated developer understanding remains unverified. The earlier mentoring-policy draft remains in its named stash, separate from this deliverable.

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

Hosted verification: [initial run 42227320592](https://github.com/minuraDinupriya/tradesphere/actions/runs/42227320592) passed backend and frontend checks but its inventory browser test timed out waiting for the uncertain-response alert. The logs did not identify the cause and no browser artifact was retained. A fresh local database (`tradesphere_trd15_ci_repro`) passed all three Edge workflows. Added a native-form-validity assertion and failure-only trace retention; [run 42227941405](https://github.com/minuraDinupriya/tradesphere/actions/runs/42227941405) on `6ee82be` passed both jobs. At that time the cause remained unexplained. **TRD-16 update:** a recurrence's retained trace identified a test navigation race selecting the old Inventory filter, not the receipt field. See the TRD-16 debugging section for evidence and the scoped-locator fix; do not reinterpret the earlier passing runs as proof of a fix already made then.

**Interview Questions**

1. **Why isn't `@Transactional` enough to prevent lost stock updates?** Two transactions can read the same old value and both overwrite it; SQL must lock, compare a version, or increment the current row atomically. This feature uses an atomic upsert.
2. **How do retries avoid duplicate receipts?** A unique organization/request key permits one insert; a retry compares its normalized payload and returns the original movement. A different payload gets 409.
3. **What if a movement inserts successfully but the balance overflows?** The runtime exception rolls back the transaction, so neither the movement nor the balance change commits. PostgreSQL integration tests verify this.
4. **Why add organization to foreign keys when IDs are UUIDs?** UUID uniqueness does not establish authorized ownership. Composite references enforce that related records belong to the same organization.
5. **What can a client infer from a network timeout?** Only that it did not receive confirmation; the server may have committed. Retry the same operation ID or reconcile history rather than assume failure.

**What I Should Practise**

1. Trace the receipt's request ID from React through the controller/service to both SQL statements. Explain in your own words which mechanism prevents partial writes, lost increments, and duplicate deliveries.
2. On a disposable exercise branch, predict and test what happens when two receipts of 7 and 11 units arrive together, then when both use one request ID with different quantities. Use database assertions, not only HTTP status codes. This is suggested practice, not work already completed by the developer.

## 2026-11-06 — TRD-16: Customer and sales pricing foundation

**Date and Jira ticket**

2026-11-06 (Asia/Colombo). [TRD-16](https://minuraranaweera.atlassian.net/browse/TRD-16), branch `feature/TRD-16-customer-sales-pricing`, from `3f49db4`. Commits: backend `d53bbc1`, frontend/tests `b40d79e`, documentation `4c0e654`, browser regression fix `15f29f7`. Update during TRD-17: [PR #4](https://github.com/minuraDinupriya/tradesphere/pull/4) is merged on main as `50ff58d`; earlier review-pending notes refer to that report. The actual Jira ticket/design guidelines were read first; approved choices are in [decision 0004](decisions/0004-customers-and-sales-pricing.md). Demonstrated understanding remains unverified; beginning a lesson is not evidence of mastery.

**What We Built**

Customer create/view/edit/search/status/pagination across PostgreSQL, Spring Boot, and React; optional contact fields and optimistic edit conflicts. Added one optional initial LKR selling price per product, exact decimal values, dedicated versioned editing, and independent customer/price write permissions. Existing users default to no new write access. Orders, taxes, payments, invoices, credit, multi-currency, deletion, and price history were not implemented. See [API](api/CUSTOMERS_AND_PRICING.md).

**Concepts I Should Understand**

- **Exact money:** Java `BigDecimal` stores decimal digits without binary floating-point approximation. Construct it from text; converting through double first can already lose information. PostgreSQL `numeric(14,2)` stores 12 whole digits and two fractional digits.
- **Validation before storage:** a two-decimal database column can round direct SQL input. The API instead rejects extra decimals and non-string JSON before persistence; the UI retains decimal strings too.
- **Unset versus zero:** null means nobody set a selling price; `"0.00"` is an explicitly set free price. Treating both as zero hides missing business data.
- **Independent edit versions and permissions:** catalog metadata and selling price have different writers and versions. One cannot overwrite the other by submitting a stale/general product form. The earlier entries explain tenant predicates, transactions, CSRF, and optimistic updates in detail.

**Real-World Analogy**

A hardware store keeps product descriptions in one section of its ledger and price labels in another. A clerk allowed to correct a bolt's description does not automatically get permission to change its price. Money is recorded as exact rupees and cents, not an approximate ruler measurement; a missing price label is different from one explicitly marked free.

**How It Works Internally**

1. [CustomerController](../backend/src/main/java/com/tradesphere/ordering/CustomerController.java) derives the organization from the authenticated principal. [CustomerService](../backend/src/main/java/com/tradesphere/ordering/CustomerService.java) normalizes/coordinates changes; [CustomerRepository](../backend/src/main/java/com/tradesphere/ordering/CustomerRepository.java) binds organization-scoped SQL and version checks.
2. [SellingPricePage](../frontend/src/features/catalog/SellingPricePage.tsx) keeps typed money as text; [sellingPriceApi](../frontend/src/features/catalog/sellingPriceApi.ts) sends that string and the retrieved version, and formats strings without `Number` conversion.
3. [SellingPriceController](../backend/src/main/java/com/tradesphere/catalog/SellingPriceController.java) validates the request; its scoped [deserializer](../backend/src/main/java/com/tradesphere/catalog/SellingPriceStringDeserializer.java) rejects JSON numbers. [SellingPriceAmount](../backend/src/main/java/com/tradesphere/catalog/SellingPriceAmount.java) constructs exact `BigDecimal` and forbids rounding.
4. [SellingPriceService](../backend/src/main/java/com/tradesphere/catalog/SellingPriceService.java) and [repository](../backend/src/main/java/com/tradesphere/catalog/SellingPriceRepository.java) atomically compare/increment `price_version`. [V5](../backend/src/main/resources/db/migration/V5__create_customers_and_selling_prices.sql) adds price columns, customers, integrity checks, and default-false sales permissions.

**Engineering Decisions**

Keep customers in Ordering and prices in Catalog rather than add services or new infrastructure. Store a nullable price on the existing product, with a separate version, rather than invent price lists/history. Reuse the existing native forms and move the abortable read hook into shared UI code so Customer does not import Inventory internals. [Decision 0004](decisions/0004-customers-and-sales-pricing.md) covers alternatives, numeric bounds, and design limitations. A future order must deliberately snapshot its price; this ticket does not define that behavior.

**Problems and Debugging**

Initial local functional checks passed. Review identified two preventative gaps: an account label understood only old permissions, and default scalar coercion could weaken the decimal-string contract. Updated the label, added scoped string deserialization, and verified separate customer-only/price-only authorization plus numeric-token rejection. Inspected the installed Jackson API with `javap` before writing its extension. No production incident or whole-feature TDD claim is made.

Hosted [run 42232319007](https://github.com/minuraDinupriya/tradesphere/actions/runs/42232319007) passed backend/frontend checks and the new sales browser journey, but the existing Inventory test failed: native validation found no receipt warehouse. Retained trace actions showed `selectOption` succeeded while snapshots still contained `inventory-warehouse`, after the URL had already changed to `/inventory/receive`. React's navigation had not committed the new page, so the generic Warehouse locator selected the old filter. Fixed the test by awaiting the receiving heading, scoping selection to the Stock receipt field group, and asserting the selected warehouse after product search. This is a test navigation/selector race, not evidence of a production receipt-state bug; retained diagnostics finally supplied evidence missing in TRD-15. All four local Edge workflows passed after the fix; hosted verification is recorded separately.

Executed locally: `.\mvnw.cmd -B -ntp verify` passed **11 unit + 32 PostgreSQL/API integration tests**, none skipped. `npm run lint`, `npm run test` (**33 component tests**), and `npm run build` passed. `npm run test:e2e` passed **4 real Edge workflows**, including existing catalog/inventory regressions and customer/pricing persistence. Screenshots were inspected at desktop and 390px customer layout. Exact commands, environments, and later hosted evidence belong in [COMMAND_LOG.md](COMMAND_LOG.md).

Hosted [run 42233121156](https://github.com/minuraDinupriya/tradesphere/actions/runs/42233121156) on `15f29f7` completed successfully after the trace-backed test fix: backend/PostgreSQL, frontend lint/tests/build/docs, real Chromium workflows, and database cleanup passed. This is separate from local Edge evidence. Later documentation-only heads can be checked in the PR; no reviewer approval, merge, production readiness, or learner understanding is inferred from CI.

Senior self-review examined tenant predicates, default-deny security routes, permission independence, catalog price tampering, monetary coercion/precision, edit races, migration compatibility, escaped search, React output escaping, and scope. No external approval or exhaustive security certification is claimed. Limits: no immediate session revocation, price history/clearing, order snapshots, exhaustive accessibility, Firefox/Safari, load tests, or production deployment. Customer-specific Figma fidelity remains unverified.

**Interview Questions**

1. **Why use `new BigDecimal("0.10")` rather than double for money?** Decimal text preserves the intended digits; binary floating-point can approximate the value before BigDecimal receives it.
2. **Does `numeric(14,2)` alone enforce rejection of three decimals?** No; PostgreSQL can round input to that scale. Validate/reject excess decimals before writing it when rounding is not allowed.
3. **Why use strings for money in JSON/React?** JavaScript numbers use binary floating point. Strings preserve exact decimal digits until validated decimal arithmetic occurs on the backend.
4. **Why separate price and catalog versions?** They represent independently authorized edits; comparing only the price version protects competing price edits without letting catalog updates write the price.
5. **How do you distinguish missing pricing from a free item?** Null means unpriced, whereas an explicitly stored zero is a price. Future ordering must decide how to handle unpriced products.

**What I Should Practise**

1. Trace `"1234.56"` from the React input through JSON, deserialization, `BigDecimal`, and JDBC. Explain where precision could be lost if a number conversion were inserted.
2. Predict two simultaneous edits using price version 0, then explain the repository SQL and integration assertion proving one winner. These are proposed learner exercises; understanding is not yet demonstrated.

## 2026-11-06 — TRD-17: Order creation and atomic reservation

**Date and Jira ticket**

2026-11-06 (Asia/Colombo). [TRD-17](https://minuraranaweera.atlassian.net/browse/TRD-17), branch `feature/TRD-17-order-stock-reservation`, from merged TRD-16/main `50ff58d`. Actual Jira requirements were retrieved before implementation. Commits: backend `c72fb2b`, useful stock conflicts `d63849f`, frontend/tests `c6c1d8f`. PR/hosted evidence is recorded when available; human merge approval and demonstrated understanding are separate states.

**What We Built**

Immutable pending multi-item orders for one customer/warehouse, server-owned LKR price/label snapshots and exact totals, list/detail/create UI, atomic confirmation, and a separate stock-reservation ledger. Same-request creation and same-order confirmation can be retried without duplicating their effects. Only an independent order-write permission permits mutations; existing users receive no automatic grant. Cancellation, fulfillment, shipping, payments, partial orders, and backorders were not implemented. Confirmed reservations remain held until a future lifecycle operation exists. See [API](api/ORDERS.md).

**Concepts I Should Understand**

- **Atomicity versus concurrency:** a transaction groups every item's reservation and the order status, so they commit together or roll back. Conditional SQL additionally prevents distinct orders from both claiming the same remaining stock. A transaction alone does not make an unlocked read/overwrite safe.
- **Locks and ordering:** an order-row update lock serializes repeated confirmations. Warehouse/customer/product share locks preserve eligibility; all product locks precede balance writes, and UUID order is consistent. This reduces deadlock cycles without claiming arbitrary future operations cannot deadlock.
- **Idempotency:** confirmation's order ID identifies one transition; the locked confirmed state returns the saved result. Creation separately uses an organization/request key and canonical payload comparison. A disabled button alone would not protect retries or multiple tabs.
- **Snapshots:** saving price/SKU/name/unit at creation preserves the quote after catalog changes. Exact line totals are generated in PostgreSQL, and the server sums BigDecimal values; widened numeric totals and JSON strings avoid overflow/JavaScript rounding. The earlier entry explains basic decimal money.

**Real-World Analogy**

A hardware-store clerk puts a numbered order slip beside the stock ledger. Confirming earmarks bolts and washers together. If washers are short, neither item is committed to that order. Two clerks must update the same ledger entry safely; showing an already stamped slip cannot earmark the goods again. Every clerk visits shared ledger entries in the same order to avoid each holding a page the other needs.

**How It Works Internally**

1. React sends customer/warehouse/product quantities and one creation request ID; the backend supplies prices, organization, actors, and state. [OrderController](../backend/src/main/java/com/tradesphere/ordering/OrderController.java) validates the boundary; [OrderRules](../backend/src/main/java/com/tradesphere/ordering/OrderRules.java) rejects duplicate/invalid lines and orders UUIDs consistently.
   The real [creation page](../frontend/src/features/ordering/OrderFormPage.tsx), [API client](../frontend/src/features/ordering/orderApi.ts), and [detail page](../frontend/src/features/ordering/OrderDetailsPage.tsx) retain retry identities and display the saved snapshot without frontend monetary arithmetic.
2. [OrderService](../backend/src/main/java/com/tradesphere/ordering/OrderService.java) locks source eligibility for creation and snapshots current [CatalogProductAccess](../backend/src/main/java/com/tradesphere/catalog/CatalogProductAccess.java) values. [OrderRepository](../backend/src/main/java/com/tradesphere/ordering/OrderRepository.java) writes headers/items using parameterized organization-scoped SQL.
3. Confirmation locks the order `FOR UPDATE`, replays confirmed state immediately, then locks eligibility before calling [InventoryOrderAccess](../backend/src/main/java/com/tradesphere/inventory/InventoryOrderAccess.java). Its `MANDATORY` transaction propagation requires the surrounding order transaction. Each balance update increases reserved only if current available >= requested; any failed line throws and rolls everything back.
4. [V6](../backend/src/main/resources/db/migration/V6__create_orders_and_stock_reservations.sql) reinforces tenant/order/warehouse/item/balance/actor relationships. Reservation rows and the confirmed header commit together; on-hand and physical receipt movements remain unchanged.

**Engineering Decisions**

Retain JDBC/PostgreSQL and public in-process module operations. Combine an order lock with conditional stock updates instead of adding optimistic retry orchestration, serializable isolation, or a broker/cache. Canonical UUID text ordering agrees with PostgreSQL even where Java UUID's signed comparison differs. Use separate reservations rather than mislabel allocations as receipts. Snapshots occur at creation and are never repriced on confirmation; quote expiry/amendment remains future policy. Bounds of 100 distinct lines and existing safe whole-unit quantities keep work finite; money stays exact. [Decision 0005](decisions/0005-order-reservation.md) records alternatives and limitations.

**Problems and Debugging**

- The first backend verification stopped at test compilation because an in-progress API test lacked its item lookup helper. Completed the helper; later complete suites passed. This was a test-code compile error, not a passing or skipped test run.
- Initial new component tests had five failures because exact label queries omitted the required-field asterisk. Replaced them with the actual accessible quantity-control queries; focused and complete suites then passed. No customer incident is invented.
- A simulated PostgreSQL trigger error intentionally fails the second reservation insertion. A temporary sequence proves two attempts occurred (sequence increments survive rollback), while committed order/balance/ledger assertions prove the business transaction rolled back. Fixture-scoped trigger/function/sequence are removed afterward; no production failure hook was added.
- Senior review found no blocking production issue but identified a database-evidence gap. Added direct-SQL tests proving the named composite foreign keys reject foreign customer/warehouse/actors and wrong reservation warehouse/item references, rather than assuming API-denial tests established database constraints.
- UI review found that a raw UUID in insufficient-stock errors did not identify a visible order line. Inventory now throws a typed stock exception carrying the product ID; Ordering translates it to its saved SKU/name without parsing error text. The API regression asserts those labels and whole-order rollback. The first new browser run also exposed an ambiguous customer selector; an explicit combobox inside the order choices group fixed the test.

Executed backend verification: `.\mvnw.cmd -B -ntp verify` passed **15 unit + 52 real PostgreSQL/API integration tests**, none skipped, including receipt/confirmation contention, reverse item order, same/distinct-order races, snapshots, retries, rollback, and foreign-key rejection. Frontend `npm run lint`, `npm run test` (**45 component tests**), and `npm run build` passed. `npm run test:e2e` passed **5 real Edge workflows** after the customer-locator correction: the new order journey verifies saved prices after a catalog change, committed confirmation with a lost response, same-order retry, and correct physical/reserved/available balances. Hosted and later post-review outcomes are recorded when executed in this entry and [COMMAND_LOG.md](COMMAND_LOG.md); they are not inferred from compilation.

Independent agent review and self-review are engineering checks, not external human approval. Limits include no general audit/tamper-proof database-role policy, reservation expiry/release, lifecycle terminal actions, quote expiry, full session revocation, exhaustive accessibility/load/failover testing, or production deployment. Pending creation retry state does not survive navigation/reload; reconcile the list before replacement. No learner understanding is claimed.

The final local backend run after typed SKU/name stock-conflict feedback passed all 67 tests again. [Actual browser screenshots](evidence/TRD-17/README.md) were inspected, including 390px detail/table behavior. Hosted execution is still a separate check, not inferred from the passing local runs.

**Interview Questions**

1. **Why is `@Transactional` insufficient by itself to prevent overselling?** Competing transactions can read the same old availability; the write must lock/recheck or conditionally update current stock. This feature uses conditional reserved increments.
2. **Why lock the order as well as stock?** Stock protection coordinates different orders; the order lock serializes repeated confirmation of the same order so its effect occurs once.
3. **What happens when the last item is unavailable?** The exception escapes the transaction, rolling back earlier balance changes, reservation rows, and the status change. Pending state remains.
4. **Why acquire locks in a consistent order?** Opposite lock sequences can create circular waits. All product eligibility locks precede deterministically ordered balance writes, consistent with current receipt operations.
5. **Why snapshot prices when creating an order?** A later catalog price change must not silently alter the saved quote. Confirmation reserves the saved quantities using the saved prices rather than recalculating from current master data.

**What I Should Practise**

1. Trace one two-item confirmation from React to SQL. Explain which statement prevents overselling, which lock prevents duplicate confirmation, and where rollback is triggered. Start with atomicity before advancing to other ACID properties.
2. Predict two orders each requesting four units when only five are available, then predict two retries of the same order. Explain the final on-hand/reserved/available numbers and compare with the PostgreSQL tests. These are suggested exercises, not demonstrated student achievements.
