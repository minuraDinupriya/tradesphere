# Architecture

Status: **foundation implemented in TRD-6; business design remains proposed**. The application skeleton, PostgreSQL connectivity, Flyway schema migration, frontend routing/layout, and CI workflow exist. No business modules, authentication, or tenant enforcement are implemented. Refine proposals using implementation evidence and record significant decisions using the [workflow](DEVELOPMENT_WORKFLOW.md).

## Application shape

Use a modular monolith: one backend deployment, organized by business capability with explicit module ownership. This keeps transactions and local development manageable for a solo developer while providing practice in clear boundaries. Microservices would introduce network failure modes, distributed transactions, and deployment overhead before the business workflow needs them.

React with TypeScript will call a Spring Boot HTTP API. The backend uses PostgreSQL with JDBC for foundation connectivity; future business persistence mapping is unresolved. The frontend placeholder currently makes no API calls. API naming conventions are defined in the workflow.

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

TRD-6 uses a Maven Wrapper and npm lockfile for repeatable builds. Flyway V1 creates the `tradesphere` schema without business tables; its history stays in `public`. Only Actuator health is exposed, without component details. Applications run on the host and PostgreSQL runs in Compose. See [foundation decision](decisions/0001-engineering-foundation.md) and [setup commands](../README.md).

## Proposed module ownership

- **Identity:** users, organization membership, authentication integration, and access policies.
- **Catalog:** product definitions and product lifecycle.
- **Inventory:** warehouses, receipts, stock balances, reservations, and reservation consumption/release.
- **Ordering:** customers initially, orders, order items, and order lifecycle orchestration. Customer ownership is provisional; extract a separate capability only when its behavior warrants it.
- **Audit:** recording and authorized retrieval of significant business/security events.

Use business-capability packages, with internal organization kept as simple as the feature needs. Modules should call explicit application interfaces and must not reach into another module's repositories or mutate its entities directly. Start with synchronous in-process calls; no message broker is required for the MVP.

## Proposed consistency strategy

An order transition and its inventory changes should share one database transaction. A transaction groups changes so they commit together or roll back together. It prevents a confirmed order from surviving without its reservations, but transaction boundaries alone do not prevent overselling.

Candidate implementation approach:

1. Resolve and verify the authenticated organization and the referenced records.
2. Lock or conditionally transition the order so concurrent lifecycle requests cannot apply its effects twice.
3. For confirmation, aggregate repeated product lines and acquire relevant stock-row locks in a deterministic order.
4. Recheck availability inside the transaction, then create reservations and update balances for all items.
5. Commit the order transition with those changes, or roll everything back on failure.

Cancellation and fulfillment should guard both the order transition and reservation state so each reservation is released or consumed once. Consistent lock ordering reduces deadlock risk; failure/retry behavior still needs design and testing.

**Proposed starting choice:** PostgreSQL row locking for understandable contention handling. Conditional updates or optimistic version checks are alternatives; they may require explicit conflict retries and careful multi-item rollback. The final choice depends on implementation and concurrency-test evidence. An unlocked read-then-write availability check is insufficient.

Database checks, unique constraints, and foreign keys should reinforce invariants where practical. Proposed reservation records should identify their order and stock scope so retries cannot duplicate reservation effects. Decide the balance/reservation schema and receipt idempotency policy before implementing them.

## Organization boundary

Propose a shared database/schema with an organization identifier on tenant-owned records. Every access path must enforce authorized organization scope. Consider organization-aware foreign/unique keys to prevent cross-organization relationships. Authentication identifies the caller; authorization determines permitted actions and organizations.

PostgreSQL row-level security is an alternative defense to evaluate, not a current commitment. Application scoping must be tested against cross-organization access regardless of the database approach. Authentication mechanism, role model, and session/token storage are open decisions.

## Audit and external effects

For core business changes, propose writing audit records within the same transaction so a failed transition does not produce a false success record. Security failure logging may require a separate path because a rolled-back business transaction cannot retain its audit writes. Define useful event fields and data minimization before implementation.

Future asynchronous external effects would need reliable delivery design, potentially an outbox. Neither a broker nor an outbox is necessary for the currently specified MVP.

## Quality and operations

Separate API input/output models from persistence details where it protects the API boundary. Validate inputs, return consistent errors, avoid exposing secrets/internal traces, and keep business rules on the backend. Exact API, error, and logging conventions remain open.

Use unit tests for local rules and PostgreSQL integration tests for migrations, constraints, transactions, concurrency, and isolation. Add focused API/UI journey coverage as features emerge. Local setup commands and CI checks must be documented only after they exist and have been verified.

Redis, RabbitMQ, advanced observability infrastructure, AWS/Azure, Terraform, Kubernetes, Helm, and Argo CD remain [later roadmap options](ROADMAP.md), contingent on a functioning MVP and an explained need.
