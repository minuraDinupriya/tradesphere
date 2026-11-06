# Architecture

Status: **TRD-6 foundation, TRD-13 catalog/minimal session access, and TRD-15 warehouse/receiving inventory are implemented**. Inventory adds transactional receipts, balances, movement history, and a separate write permission. Order reservations, Ordering, and a general Audit capability remain proposed. Refine proposals using evidence and record significant decisions using the [workflow](DEVELOPMENT_WORKFLOW.md).

## Application shape

Use a modular monolith: one backend deployment, organized by business capability with explicit module ownership. This keeps transactions and local development manageable for a solo developer while providing practice in clear boundaries. Microservices would introduce network failure modes, distributed transactions, and deployment overhead before the business workflow needs them.

React with TypeScript calls the Spring Boot product/session/inventory HTTP APIs. Catalog and Inventory use explicit JDBC persistence and immutable records; no ORM is required for these slices. API naming conventions are defined in the workflow. The [TRD-13 decision](decisions/0002-product-catalog-and-session-access.md) and [TRD-15 decision](decisions/0003-warehouse-inventory.md) explain current data flow and tradeoffs. Shared HTTP exception translation lives in `com.tradesphere.api`.

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

## Implemented receipt consistency

`InventoryService.receive` groups an append-only application movement and balance increase in one transaction. A unique `(organization_id, request_id)` key deduplicates concurrent retries; identical normalized payloads replay the original receipt, and changed payloads conflict. An atomic PostgreSQL upsert increments the existing balance without an unlocked read/overwrite. Overflow rolls back the movement too. Warehouse/product share locks prevent deactivation during a new receipt. The catalog eligibility check uses the public `CatalogProductAccess` service operation, not its repository.

Balances enforce nonnegative on-hand/reserved and `reserved <= on_hand`; `available` is generated as their difference. Whole-unit values are limited to JavaScript's exact integer range. Inventory's read projections join current catalog/user labels with organization predicates; this deliberate read-only coupling is recorded in [decision 0003](decisions/0003-warehouse-inventory.md). Inventory does not mutate those modules' data. Movement identifiers/quantity/actor/time are persisted, but current labels are not historical snapshots and the database is not a tamper-proof audit store.

## Proposed order consistency strategy

An order transition and its inventory changes should share one database transaction. A transaction groups changes so they commit together or roll back together. It prevents a confirmed order from surviving without its reservations, but transaction boundaries alone do not prevent overselling.

Candidate implementation approach:

1. Resolve and verify the authenticated organization and the referenced records.
2. Lock or conditionally transition the order so concurrent lifecycle requests cannot apply its effects twice.
3. For confirmation, aggregate repeated product lines and acquire relevant stock-row locks in a deterministic order.
4. Recheck availability inside the transaction, then create reservations and update balances for all items.
5. Commit the order transition with those changes, or roll everything back on failure.

Cancellation and fulfillment should guard both the order transition and reservation state so each reservation is released or consumed once. Consistent lock ordering reduces deadlock risk; failure/retry behavior still needs design and testing.

**Proposed starting choice:** PostgreSQL row locking for understandable contention handling. Conditional updates or optimistic version checks are alternatives; they may require explicit conflict retries and careful multi-item rollback. The final choice depends on implementation and concurrency-test evidence. An unlocked read-then-write availability check is insufficient.

Database checks, unique constraints, and foreign keys reinforce receipt invariants in V4. Proposed reservation records should identify their order and stock scope so retries cannot duplicate reservation effects. The balance schema and receipt retry policy now exist; reservation records and order transitions still need design before implementation.

## Organization boundary

Propose a shared database/schema with an organization identifier on tenant-owned records. Every access path must enforce authorized organization scope. Consider organization-aware foreign/unique keys to prevent cross-organization relationships. Authentication identifies the caller; authorization determines permitted actions and organizations.

TRD-13 uses Spring Security server-side sessions, BCrypt hashes, and CSRF protection. Identity records bind each user to one organization; controllers derive scope from `OrganizationUser`, and catalog/inventory queries include it. Payloads never establish organization authority. V4 composite foreign keys also prevent cross-organization warehouse/product/actor references. All authenticated organization users may read inventory; only `inventory:write` permits warehouse mutations/receipts. Existing users default to no inventory-write access, while explicit fresh-database bootstrap creates an initial editor. Full membership/role administration remains open. PostgreSQL row-level security is an alternative defense to evaluate, not implemented; actual application scoping is covered by PostgreSQL/API tests.

## Audit and external effects

For core business changes, propose writing audit records within the same transaction so a failed transition does not produce a false success record. Security failure logging may require a separate path because a rolled-back business transaction cannot retain its audit writes. Define useful event fields and data minimization before implementation.

Future asynchronous external effects would need reliable delivery design, potentially an outbox. Neither a broker nor an outbox is necessary for the currently specified MVP.

## Quality and operations

Separate API input/output models from persistence details where it protects the API boundary. Validate inputs, return consistent errors, avoid exposing secrets/internal traces, and keep business rules on the backend. Exact API, error, and logging conventions remain open.

Use unit tests for local rules and PostgreSQL integration tests for migrations, constraints, transactions, concurrency, and isolation. Add focused API/UI journey coverage as features emerge. Local setup commands and CI checks must be documented only after they exist and have been verified.

Redis, RabbitMQ, advanced observability infrastructure, AWS/Azure, Terraform, Kubernetes, Helm, and Argo CD remain [later roadmap options](ROADMAP.md), contingent on a functioning MVP and an explained need.
