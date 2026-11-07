# Architecture

Status: **TRD-6/TRD-13/TRD-15/TRD-16/TRD-17 are merged; TRD-18 adds order lifecycle and scoped audit**. A general Audit capability remains proposed. Refine proposals using evidence and record significant decisions using the [workflow](DEVELOPMENT_WORKFLOW.md).

## Application shape

TRD-19 adds a read-only Dashboard projection using three bounded, organization-scoped JDBC queries in a repeatable-read transaction. It deliberately reads existing capability tables without mutating them or duplicating business rules. Counts distinguish active master data, all warehouses/orders, and recorded zero-availability balances; quantities across unlike units are never summed. Existing created-at/organization indexes support recent-order retrieval. No cache/materialized aggregate is introduced without evidence of a need. See [API semantics](api/DASHBOARD.md).

The proposed demo container packages React assets with Spring Boot for a single origin, retaining session/CSRF protections. Runtime is non-root; build tools remain in build stages. Secure cookies and trusted ingress forwarding belong to the explicit demo profile. Container CI builds and smoke-checks readiness, SPA fallback, and anonymous API denial. Remote deployment remains unapproved/unverified; see [deployment](DEPLOYMENT.md).

Use a modular monolith: one backend deployment, organized by business capability with explicit module ownership. This keeps transactions and local development manageable for a solo developer while providing practice in clear boundaries. Microservices would introduce network failure modes, distributed transactions, and deployment overhead before the business workflow needs them.

React with TypeScript calls Spring Boot session/catalog/customer/inventory/order HTTP APIs. Modules use explicit JDBC persistence and immutable records; no ORM is required for these slices. API naming follows the workflow. The [catalog decision](decisions/0002-product-catalog-and-session-access.md), [inventory decision](decisions/0003-warehouse-inventory.md), and [order decision](decisions/0005-order-reservation.md) explain data flow/tradeoffs. Shared HTTP exception translation lives in `com.tradesphere.api`.

## Baseline technologies and purpose

- **Java 21 LTS:** initial backend language/runtime baseline. Confirm compatible dependency versions during setup.
- **Spring Boot 4.1.2:** application configuration and backend framework; selected from stable metadata and compatible with Java 21.
- **Spring Security:** backend authentication and authorization integration; it does not automatically supply the project's organization-isolation rules.
- **Maven:** backend dependencies, builds, and test execution.
- **PostgreSQL:** relational data integrity and transactional inventory/order updates.
- **Flyway:** versioned database migrations, applied consistently across environments.
- **React and TypeScript:** interactive client UI with compile-time type checking. Backend validation remains authoritative.
- **Docker Compose:** PostgreSQL 18.6 only, with a health check, loopback port binding, and persistent named volume.
- **Automated tests and CI/CD:** PostgreSQL integration tests plus frontend component/navigation tests; GitHub Actions runs validation on PRs to `main` and pushes to `main`. Deployment and hosting remain unresolved.

Do not select exact framework versions from these documents alone; verify supported, compatible releases when scaffolding is authorized.

TRD-6 uses a Maven Wrapper and npm lockfile for repeatable builds. Flyway V1 creates the `tradesphere` schema; V2 adds organizations/users, V3 adds products, and V4 adds warehouses/balances/receipt movements and inventory permission. History stays in `public`. Only Actuator health (including readiness) is public, without component details. Applications run on the host and PostgreSQL runs in Compose. See [foundation decision](decisions/0001-engineering-foundation.md) and [setup commands](../README.md).

## Proposed module ownership

- **Identity:** users, organization membership, authentication integration, and access policies.
- **Catalog:** product definitions and product lifecycle.
- **Inventory:** warehouses, receipts, stock balances, reservations, and reservation consumption/release.
- **Ordering:** customers initially, orders, order items, and order lifecycle orchestration. Customer ownership is provisional; extract a separate capability only when its behavior warrants it.
- **Audit:** recording and authorized retrieval of significant business/security events.

Use business-capability packages, with internal organization kept as simple as the feature needs. Modules should call explicit application interfaces and must not reach into another module's repositories or mutate its entities directly. Start with synchronous in-process calls; no message broker is required for the MVP.

## Customer and price foundation

TRD-16 implements customers within Ordering, retaining its existing provisional ownership without implementing orders. Catalog owns a nullable LKR price on each product, represented by `BigDecimal`/PostgreSQL `numeric(14,2)` and decimal strings over HTTP/React. Independent customer and price write authorities prevent catalog/inventory permissions from implicitly granting sales mutations. Customer and price edits use atomic version checks; price has its own version so catalog edits cannot overwrite it. Price input rejects extra decimal places before SQL and a scoped deserializer rejects JSON numeric coercion. See [decision 0004](decisions/0004-customers-and-sales-pricing.md) and [API](api/CUSTOMERS_AND_PRICING.md).

V5 adds customers, selling-price/price-version columns, and default-false permission flags; existing users are not upgraded to writers. Shared React `useRemoteData` owns abortable reads. Identity lifecycle and general price history remain deferred; TRD-17 implements the creation-time order snapshots described below.

## Receipt consistency

`InventoryService.receive` groups an append-only application movement and balance increase in one transaction. A unique `(organization_id, request_id)` key deduplicates concurrent retries; identical normalized payloads replay the original receipt, and changed payloads conflict. An atomic PostgreSQL upsert increments the existing balance without an unlocked read/overwrite. Overflow rolls back the movement too. Warehouse/product share locks prevent deactivation during a new receipt. The catalog eligibility check uses the public `CatalogProductAccess` service operation, not its repository.

Balances enforce nonnegative on-hand/reserved and `reserved <= on_hand`; `available` is generated as their difference. Whole-unit values are limited to JavaScript's exact integer range. Inventory's read projections join current catalog/user labels with organization predicates; this deliberate read-only coupling is recorded in [decision 0003](decisions/0003-warehouse-inventory.md). Inventory does not mutate those modules' data. Movement identifiers/quantity/actor/time are persisted, but current labels are not historical snapshots and the database is not a tamper-proof audit store.

## Implemented order confirmation

TRD-17's `OrderService` owns the order transaction and price/label snapshots; `CatalogProductAccess` and `InventoryOrderAccess` expose explicit operations requiring that transaction. V6 adds pending/confirmed headers/items, a separate reservation ledger, default-false order permission, and composite tenant references. Creation uses an organization/request unique key and canonical payload comparison. Confirmation locks the tenant-scoped order row, returns a confirmed replay immediately, then acquires warehouse/customer and all sorted product eligibility locks before any sorted balance writes.

Inventory conditionally increments reserved stock only when available is sufficient; a later-line failure rolls back earlier increments, ledger rows, and the header transition. On-hand and physical receipt history do not change. Deterministic UUID text ordering matches PostgreSQL UUID sorting. This protocol accounts for current receipt locks; it is not a universal guarantee against deadlocks in unspecified future operations. Price snapshots are BigDecimal/numeric values returned as strings; header/line totals have wider precision than product prices. See [decision 0005](decisions/0005-order-reservation.md) and [order API](api/ORDERS.md).

## Implemented lifecycle consistency (TRD-18)

`OrderTransitions` defines the approved graph; `OrderLifecycleService` owns processing, cancellation, and fulfillment transactions. Every mutation locks the tenant-scoped order first, checks recorded action history for replay, then validates the current state. A retry can return a later current state without repeating earlier effects. Existing `OrderService.confirm` uses the same history guard. Order-write access and CSRF are enforced before controllers derive organization/actor from the session.

Inventory owns reservation closure and its movement ledger through `InventoryOrderAccess`. Cancellation of confirmed orders and fulfillment lock that order's reservations, verify the full expected item/quantity set is active, then update shared balances in PostgreSQL UUID order. Conditional balance/closure updates, reservation statuses, unique movement keys, and the order lock protect once-only effects. Any exception rolls back balances, reservations, state, movements, and transition history together. Source deactivation does not prevent closing already-reserved orders.

Cancellation releases reserved stock while preserving on-hand; fulfillment reduces both equally. Pending cancellation and starting processing change no balances. Cancellation stops at processing, and fulfillment requires processing. Terminal states cannot be reopened.

V7 adds `ACTIVE/RELEASED/CONSUMED` reservation closure metadata, order transitions, and a separate `order_inventory_movements` ledger for release/fulfillment deltas. Receipt-only `stock_movements` and its API retain their meaning. Ordering/Inventory own these narrow records; no general Audit module or event bus is added. Composite foreign keys preserve tenant/order/warehouse/actor relationships. Existing creation/confirmation metadata is backfilled and explicitly marked as reconstructed.

The history response uses a repeatable-read snapshot for transitions and movements. Timestamps use PostgreSQL transaction time (`now()`), so history is ordered by the acyclic lifecycle sequence, then time/ID; timestamps are not commit-time evidence. Actor IDs are historical; usernames are current labels. These application records are not database-role-enforced immutable or tamper-proof audit storage.

The lock protocol coordinates current receipt, confirmation, release, and fulfillment paths. Deterministic ordering reduces deadlock risk; tests do not establish universal deadlock freedom or load/failover guarantees. Optimistic retries/serializable transactions remain alternatives with complexity costs. See [decision 0006](decisions/0006-order-lifecycle.md) and [order API](api/ORDERS.md).

## Organization boundary

Propose a shared database/schema with an organization identifier on tenant-owned records. Every access path must enforce authorized organization scope. Consider organization-aware foreign/unique keys to prevent cross-organization relationships. Authentication identifies the caller; authorization determines permitted actions and organizations.

TRD-13 uses Spring Security server-side sessions, BCrypt hashes, and CSRF protection. Identity records bind each user to one organization; controllers derive scope from `OrganizationUser`, and catalog/inventory queries include it. Payloads never establish organization authority. V4 composite foreign keys also prevent cross-organization warehouse/product/actor references. All authenticated organization users may read inventory; only `inventory:write` permits warehouse mutations/receipts. Existing users default to no inventory-write access, while explicit fresh-database bootstrap creates an initial editor. Full membership/role administration remains open. PostgreSQL row-level security is an alternative defense to evaluate, not implemented; actual application scoping is covered by PostgreSQL/API tests.

## Audit and external effects

TRD-18 writes order transitions and closure movements in the business transaction so a failed change cannot leave a false success event. General audit remains future scope. Security failure logging may require a separate path because a rolled-back business transaction cannot retain its audit writes; define useful event fields and data minimization before expanding it.

Future asynchronous external effects would need reliable delivery design, potentially an outbox. Neither a broker nor an outbox is necessary for the currently specified MVP.

## Quality and operations

Separate API input/output models from persistence details where it protects the API boundary. Validate inputs, return consistent errors, avoid exposing secrets/internal traces, and keep business rules on the backend. Exact API, error, and logging conventions remain open.

Use unit tests for local rules and PostgreSQL integration tests for migrations, constraints, transactions, concurrency, and isolation. Add focused API/UI journey coverage as features emerge. Local setup commands and CI checks must be documented only after they exist and have been verified.

Redis, RabbitMQ, advanced observability infrastructure, AWS/Azure, Terraform, Kubernetes, Helm, and Argo CD remain [later roadmap options](ROADMAP.md), contingent on a functioning MVP and an explained need.
