# TradeSphere

TradeSphere is a B2B commerce and supply-chain management portfolio project for **Lanka Distribution Group**, a fictional Sri Lankan distributor operating three warehouses.

The system will manage products, warehouses, stock receipts, inventory availability, customers, and orders. It is a solo flagship software engineering portfolio project, with an emphasis on learning, explainable engineering decisions, and preparation for Sri Lankan software engineering internship interviews beginning in **January 2027**.

## Current status

TRD-6 establishes the engineering foundation: a Spring Boot health endpoint, PostgreSQL connectivity and Flyway schema migration, a React placeholder with routing/layout, automated checks, and a GitHub Actions CI workflow. Business features, authentication/authorization, tenant enforcement, Stitch designs, and deployment remain unimplemented. Do not use this foundation as a secured application.

## Technical baseline

Java 21 LTS, Spring Boot 4.1.2, Maven Wrapper, React 19 with TypeScript and Vite 8, PostgreSQL 18.6, Flyway, Docker Compose, and GitHub Actions CI. The backend is a modular monolith skeleton with Identity, Catalog, Inventory, Ordering, and Audit package boundaries. Spring Security will be introduced with authentication, not before.

See [architecture](docs/ARCHITECTURE.md) for rationale and open choices, and the [TRD-6 decision](docs/decisions/0001-engineering-foundation.md) for foundation choices. Dependency versions are recorded in the POM and npm lockfile; later domain design remains provisional.

## Repository layout

- `backend/`: Spring Boot application, Maven Wrapper, migrations, and PostgreSQL integration tests.
- `frontend/`: React application; shared layout in `src/app/`, business UI grouped in `src/features/`.
- `compose.yaml`: local PostgreSQL, health check, and persistent named volume.
- `.github/`: pull request template and CI workflow.
- `docs/`: context, requirements, decisions, workflow, mentoring, and learning journal.

## Run locally (PowerShell)

Prerequisites: JDK 21, Node.js 24 LTS with npm, and Docker Desktop running Linux containers with Docker Compose. Set `JAVA_HOME` to your JDK 21 installation and put its `bin` directory first on `PATH`; check `java -version`. If `docker` is not recognized, add Docker Desktop's `resources/bin` directory to the current shell's `PATH`.

From the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env: replace POSTGRES_PASSWORD with a unique local password.
docker compose config --quiet
docker compose up -d --wait --wait-timeout 120
docker compose ps
```

Do not overwrite an existing `.env`. Keep its values unquoted; simple alphanumeric passwords avoid differences between Compose interpolation and Java properties parsing. `.env` is ignored by Git. `POSTGRES_PORT` defaults to 5432; change it if the port is occupied. PostgreSQL is exposed only on loopback.

In a second terminal:

```powershell
Set-Location backend
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

Spring reads the root `.env` when launched from `backend/` (or the repository root). Shell environment variables override file values. Flyway applies migrations before startup completes. Open [backend health](http://127.0.0.1:8080/actuator/health); a healthy response includes `"status":"UP"` and may list health group names. Only the health endpoint is exposed; component details remain hidden. There are no business APIs yet.

In a third terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open [TradeSphere](http://127.0.0.1:5173/). Vite serves the UI and reserves `/api` proxying to port 8080 for future endpoints; the placeholder does not fetch business data. If you change `SERVER_PORT`, also update the proxy when API work begins. The UI is an independent foundation preview, not an implementation of the unprovided Stitch assets.

On Linux/macOS, use `./mvnw` instead of `.\mvnw.cmd`. Node and Compose commands are the same.

## Validation

With PostgreSQL running, execute `./mvnw -B -ntp verify` from `backend/` (Windows: `.\mvnw.cmd -B -ntp verify`). Maven Failsafe runs `*IT` integration tests against the configured PostgreSQL database, checking migration history, connectivity, live HTTP health, and unexposed configuration endpoints. Use a disposable development/test database, never production. No business unit tests exist yet; `mvn test` alone does not run the integration suite.

From `frontend/`:

```powershell
npm run lint
npm run test
npm run build
```

Vitest and Testing Library cover the preview, route recovery, and skip link. The build also runs TypeScript checks. `npm run test:watch` supports interactive testing; `npm run preview` serves the production build locally.

[Foundation CI](.github/workflows/ci.yml) runs backend/Compose checks and frontend install/lint/tests/build on PRs targeting `main` and pushes to `main`. It creates a disposable database with a generated password and removes that CI volume afterward. No production deployment is configured. See the [learning journal](docs/LEARNING_JOURNAL.md) for actual verification evidence and limitations.

## Stop and troubleshoot

Stop application terminals with Ctrl+C. `docker compose down` stops PostgreSQL while retaining the named volume. Avoid `down --volumes` for normal development: it deletes stored database data.

- Check `docker compose ps` and `docker compose logs postgres` if PostgreSQL does not become healthy.
- A compiler error about release 21 usually means Maven is using the wrong JDK; check `.\mvnw.cmd -version`.
- Missing/incorrect database configuration prevents startup; check the ignored `.env` and current shell overrides without sharing passwords in logs or PRs.
- Changing `POSTGRES_PASSWORD`, `POSTGRES_USER`, or `POSTGRES_DB` does not reconfigure an already initialized PostgreSQL volume. Use the existing credentials or deliberately migrate/reset a disposable database after considering data loss.
- Applied Flyway migrations should not be edited; add the next versioned migration so existing databases retain a consistent history.

## MVP boundaries

Orders may contain multiple items but use one warehouse. Confirming an order must atomically reserve available stock without overselling. Eligible cancellation releases reservations exactly once; fulfillment consumes reservations and physical stock consistently. The MVP excludes backorders, partial fulfillment, and real payments.

Tenant-owned data must be isolated by organization, and authorization must be enforced on the backend. Detailed acceptance expectations are in [requirements](docs/REQUIREMENTS.md).

## Documentation guide

- [AGENTS.md](AGENTS.md): concise persistent instructions for coding agents.
- [Mentoring guidelines](docs/MENTORING_GUIDELINES.md): teaching approach and automatic journal maintenance.
- [Learning journal](docs/LEARNING_JOURNAL.md): completed engineering milestones, actual validation, and interview revision.
- [Project context](docs/PROJECT_CONTEXT.md): business scenario, learning goals, and assumptions.
- [Requirements](docs/REQUIREMENTS.md): scope, business rules, and acceptance expectations.
- [Architecture](docs/ARCHITECTURE.md): proposed design, module ownership, and unresolved choices.
- [Development workflow](docs/DEVELOPMENT_WORKFLOW.md): deliverables, review, testing, and decision records.
- [Roadmap](docs/ROADMAP.md): planned sequence and deferred technologies.

All business examples and future demo data represent a simulated organization. Planned capabilities must not be presented as completed work.
