# 0001: TRD-6 engineering foundation

Date: 2026-11-06 (Asia/Colombo)

Status: accepted for this implementation within the developer-authorized TRD-6 scope; not an external approval or a merge approval.

## Context

The solo project needs reproducible local development and meaningful automated checks before business functionality. The developer supplied [TRD-6 scope in chat](../PROJECT_CONTEXT.md); no Jira URL is known. No Stitch files have been supplied for implementation.

## Decisions and alternatives

- Use a single Java 21/Spring Boot 4.1.2 backend with capability packages. Package documentation establishes boundaries without speculative services, repositories, or module frameworks. Microservices would add deployment and consistency problems without a demonstrated need.
- Use Spring JDBC for the initial database connection and health checks. No business persistence model is needed yet, so JPA is deferred rather than ruled out.
- Let Boot manage compatible transitive versions. Keep the Maven Wrapper and npm lockfile in Git; use Node 24 and the current stable React/TypeScript/Vite scaffold. Avoid snapshot/RC Spring releases.
- Run PostgreSQL 18.6 in Compose and applications on the host for fast local iteration. Mount `/var/lib/postgresql` for the PostgreSQL 18 image layout. Use a named volume and loopback networking. Full application containerization can wait.
- Use Flyway V1 to create an application schema, not speculative product/order tables. Keep migration history in `public`; future business tables should explicitly use `tradesphere`.
- Test actual PostgreSQL and HTTP behavior using Maven Failsafe `*IT` tests. Reuse Compose locally and in CI rather than adding Testcontainers immediately. This requires starting the database before `verify`; H2 would not verify PostgreSQL behavior.
- Use React Router with a shared layout and feature folders. Use Vitest, jsdom, and Testing Library for user-visible behavior; retain the scaffold's Oxlint and TypeScript checks. No state-management framework or component library is needed for a placeholder.
- Expose only minimal Actuator health and bind local application servers to loopback. Spring Security is deferred until authentication. No tenant-owned data or business endpoints are exposed by this milestone.
- Use GitHub Actions CI for builds/tests, with disposable generated database credentials. Do not configure production deployment, extra tickets, or automatic merging.

## Consequences and verification

This is a working foundation, not a secured commerce system. Package boundaries are conventions at this stage, not automatically enforced module isolation. Component tests do not replace real browser validation; database integration tests do not prove future inventory concurrency behavior.

See [TRD-6 journal](../LEARNING_JOURNAL.md) for executed checks, genuine debugging findings, and remaining limitations. The pull request must remain unmerged until the developer approves it.

Sources checked during setup: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Vite setup](https://vite.dev/guide/), and [official PostgreSQL container documentation](https://hub.docker.com/_/postgres).
