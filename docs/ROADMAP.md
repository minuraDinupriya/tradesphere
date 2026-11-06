# Roadmap

Status: TRD-6 and TRD-13 are merged; TRD-15 implements warehouse management, receiving, balances, and receipt history. Full identity administration, customers, and order behavior remain planned. This is a planned sequence, not a fixed delivery schedule; see the [learning journal](LEARNING_JOURNAL.md) for actual evidence and PR status.

The goal is a defensible, functioning MVP before adding advanced infrastructure. Internship interviews begin in January 2027; prioritize working core flows and the ability to explain them over completing every phase by that date.

## Phase 0: documentation foundation

The project context, requirements, proposed architecture, agent instructions, workflow, and learning journal are established. Open questions remain explicitly labeled. The developer authorized TRD-6 for engineering foundation setup.

## Phase 1: runnable foundation and access boundary

TRD-6 establishes Git/branch/PR conventions, compatible scaffolds, PostgreSQL/Flyway, local setup, and CI. TRD-13 adds the separately approved minimal sign-in and catalog access boundary, including cross-organization tests. Full membership administration remains future scope.

Exit evidence: documented setup works, migrations and available checks run, and an initial protected flow demonstrates backend access control and organization isolation.

## Phase 2: catalog, warehouses, receipts, and availability

Deliver incremental working slices for products, the three fictional warehouses, receipts, and stock availability. Resolve fields and quantity semantics as each feature requires. Add corresponding UI and tests instead of postponing all integration until the end.

TRD-13 covers product catalog. TRD-15 adds warehouses, one-product receipts, balances, and movement history with separate inventory-write access and PostgreSQL retry/concurrency tests. Active status now gates new receipts; order eligibility/reservations are still unimplemented. Corrections, transfers, and multi-line deliveries are not part of the implemented slice.

Exit evidence: a receipt changes the correct balance, availability follows the stock formula, and unauthorized or cross-organization access is rejected.

## Phase 3: customers and order correctness

Implement customer management and the single-warehouse order workflow. Resolve lifecycle eligibility and API retry behavior. Cover atomic confirmation, contention, exactly-once cancellation effects, fulfillment, and relevant audit records.

Exit evidence: the [requirements' acceptance scenarios](REQUIREMENTS.md) pass, including PostgreSQL concurrency tests and rollback/isolation cases, with usable frontend journeys.

## Phase 4: MVP delivery and interview readiness

Complete the core end-to-end workflow, improve error handling and usability, review security and documentation, and prepare clearly fictional demo data. Select a simple deployment approach and implement a validated CI/CD path when the MVP is ready for it.

Exit evidence: reproducible setup and demo, actual validation results, documented architecture decisions, known limitations, and explanations the developer can defend. A deployment is only complete when its result has been verified.

## Later options: introduce only with a demonstrated need

- **Redis:** evaluate for a measured caching or ephemeral-state need; preserve PostgreSQL as the source of truth for stock correctness.
- **RabbitMQ:** evaluate for a real asynchronous workflow, with delivery guarantees, retries, and idempotency understood first.
- **Observability:** evolve basic application logs and health checks into metrics/tracing when operational questions justify them. Basic diagnosability belongs in the MVP; a full platform can wait.
- **AWS or Azure:** choose a deployment target based on learning value, cost, and operational needs. Using both is not a requirement.
- **Terraform:** add when infrastructure exists and reproducible provisioning has a concrete benefit.
- **Kubernetes, Helm, and Argo CD:** evaluate only after simpler deployment works and orchestration/GitOps learning goals justify the maintenance cost.

None of these technologies is committed MVP scope. Record the problem, alternatives, expected benefit, and operational cost before adoption. A modular monolith can remain the right architecture throughout this roadmap.

Use the [development workflow](DEVELOPMENT_WORKFLOW.md) to turn the next outcome into one deliverable at a time. New scope and unrelated work require the developer's approval.
