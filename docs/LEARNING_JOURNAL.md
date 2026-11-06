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
4. [main.tsx](../frontend/src/main.tsx) mounts React and the browser router. [App.tsx](../frontend/src/App.tsx) chooses the route; [AppLayout](../frontend/src/app/AppLayout.tsx) supplies navigation and renders its `Outlet`; [DashboardPage](../frontend/src/features/dashboard/DashboardPage.tsx) renders the clearly labeled preview. Unknown routes offer a return link.
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
