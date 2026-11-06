# TradeSphere

TradeSphere is a planned B2B commerce and supply-chain management system for **Lanka Distribution Group**, a fictional Sri Lankan distributor operating three warehouses.

The system will manage products, warehouses, stock receipts, inventory availability, customers, and orders. It is a solo flagship software engineering portfolio project, with an emphasis on learning, explainable engineering decisions, and preparation for Sri Lankan software engineering internship interviews beginning in **January 2027**.

## Current status

Documentation foundation only. No backend, frontend, database migrations, automated tests, or CI/CD pipeline have been implemented. Application scaffolding and Git initialization are outside this initial documentation deliverable. There are no runnable application commands yet.

## Proposed technical baseline

Java 21 LTS, Spring Boot, React with TypeScript, PostgreSQL, Maven, Flyway, Spring Security, Docker Compose, automated tests, and CI/CD. The backend will begin as a modular monolith organized around Identity, Catalog, Inventory, Ordering, and Audit.

This baseline is a proposal, not evidence of installed software or a finalized implementation. See [architecture](docs/ARCHITECTURE.md) for rationale and open choices.

## MVP boundaries

Orders may contain multiple items but use one warehouse. Confirming an order must atomically reserve available stock without overselling. Eligible cancellation releases reservations exactly once; fulfillment consumes reservations and physical stock consistently. The MVP excludes backorders, partial fulfillment, and real payments.

Tenant-owned data must be isolated by organization, and authorization must be enforced on the backend. Detailed acceptance expectations are in [requirements](docs/REQUIREMENTS.md).

## Documentation guide

- [AGENTS.md](AGENTS.md): concise persistent instructions for coding agents.
- [Mentoring guidelines](docs/MENTORING_GUIDELINES.md): teaching approach and automatic journal maintenance.
- [Learning journal](docs/LEARNING_JOURNAL.md): completed engineering milestones and interview revision; currently awaiting the first implementation milestone.
- [Project context](docs/PROJECT_CONTEXT.md): business scenario, learning goals, and assumptions.
- [Requirements](docs/REQUIREMENTS.md): scope, business rules, and acceptance expectations.
- [Architecture](docs/ARCHITECTURE.md): proposed design, module ownership, and unresolved choices.
- [Development workflow](docs/DEVELOPMENT_WORKFLOW.md): deliverables, review, testing, and decision records.
- [Roadmap](docs/ROADMAP.md): planned sequence and deferred technologies.

All business examples and future demo data represent a simulated organization. Planned capabilities must not be presented as completed work.
