# TradeSphere

TradeSphere is a B2B commerce and supply-chain management portfolio project for **Lanka Distribution Group**, a fictional Sri Lankan distributor operating three warehouses.

The system will manage products, warehouses, stock receipts, inventory availability, customers, and orders. It is a solo flagship software engineering portfolio project, with an emphasis on learning, explainable engineering decisions, and preparation for Sri Lankan software engineering internship interviews beginning in **January 2027**.

## Current status

TRD-19 adds the organization-scoped operational dashboard and local same-origin container preparation. See the [dashboard API](docs/api/DASHBOARD.md), [deployment proposal and recovery guide](docs/DEPLOYMENT.md), [interview demo](docs/DEMO_GUIDE.md), and [unreleased version notes](CHANGELOG.md). Public hosting, spending, release tags, and merge remain approval-gated; nothing is claimed deployed. Dashboard refresh retrieves a consistent database snapshot, not a live push stream.

TRD-6, TRD-13, TRD-15, TRD-16, and TRD-17 are merged. TRD-18 adds processing, eligible cancellation, full fulfillment, and organization-scoped order history. Cancellation releases reserved stock once; fulfillment reduces physical and reserved stock together. Existing workflows remain regression coverage. Shipping, payments, returns, partial fulfillment, backorders, taxes, invoices, credit, multi-currency, full identity administration, and deployment remain excluded. This is a local portfolio implementation; actual verification and PR status are in the [learning journal](docs/LEARNING_JOURNAL.md).

## Technical baseline

Java 21 LTS, Spring Boot 4.1.2 with Spring Security/Validation, Maven Wrapper, React 19 with TypeScript and Vite 8, PostgreSQL 18.6, Flyway, Docker Compose, and GitHub Actions CI. The backend is a modular monolith using JDBC. Ordering and Inventory own their lifecycle history and stock records; a general Audit module remains planned. See the [order/reservation decision](docs/decisions/0005-order-reservation.md) and [lifecycle decision](docs/decisions/0006-order-lifecycle.md).

See [architecture](docs/ARCHITECTURE.md), the [foundation decision](docs/decisions/0001-engineering-foundation.md), the [catalog/security decision](docs/decisions/0002-product-catalog-and-session-access.md), and the [inventory decision](docs/decisions/0003-warehouse-inventory.md) for rationale and open choices. Dependency versions are recorded in the POM and npm lockfile; later domain design remains provisional.

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

For the first account only, edit the ignored root `.env`: set `BOOTSTRAP_ENABLED=true`, choose a `BOOTSTRAP_USERNAME` (3–80 lowercase letters/numbers/dots/hyphens/underscores), a unique `BOOTSTRAP_PASSWORD` (at least 12 characters, at most 72 UTF-8 bytes), and `BOOTSTRAP_ORGANIZATION_NAME=Lanka Distribution Group`. No default password is committed. Bootstrap works only when the user table is empty; it creates one organization/editor with catalog and inventory write permissions and never changes existing credentials or permissions. After successful startup, set `BOOTSTRAP_ENABLED=false` and remove the bootstrap password after storing it securely. Registration, password reset, and account administration are not included.

**Upgrading an existing database:** V4 gives existing users `can_write_inventory=false`; catalog editors do not silently gain inventory write access. A database owner must explicitly grant that flag to a chosen organization-bound account, then the user must sign out and in to refresh their session. See [inventory permission provisioning](docs/api/INVENTORY.md#permission-provisioning) for suggested SQL; that grant was not executed on existing development accounts by the agent.

V5 similarly defaults `can_write_customers` and `can_write_prices` to false for existing accounts. Explicit fresh-empty-database bootstrap now creates the initial editor with all four write permissions. Select grants deliberately using the [sales permission guide](docs/api/CUSTOMERS_AND_PRICING.md#errors-and-permission-provisioning), then sign out/in. No existing development user's permissions were changed by the agent. Prices start unset; zero is an explicit price, and extra decimal places are rejected rather than rounded.

V6 adds a fifth, independent `can_write_orders` flag defaulting false for existing users. Fresh-empty-database bootstrap grants it to the initial editor; existing users require an explicit owner grant and sign-out/in. See [order setup and API](docs/api/ORDERS.md#errors-setup-and-exclusions). Older browser-test databases likewise need deliberate provisioning or a fresh disposable database; TRD-17 uses `tradesphere_trd17_e2e` rather than modifying old test accounts.

```powershell
Set-Location backend
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

Spring reads the root `.env` when launched from `backend/` (or the repository root). Shell environment variables override file values. Flyway applies migrations before startup completes. Open [backend health](http://127.0.0.1:8080/actuator/health); a healthy response includes `"status":"UP"`. `/actuator/health/readiness` becomes ready after startup/bootstrap completes. Health/component details remain hidden; other Actuator endpoints are not exposed. Business endpoints require a session; see the [catalog API](docs/api/PRODUCT_CATALOG.md) and [inventory API](docs/api/INVENTORY.md).

In a third terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open [TradeSphere](http://127.0.0.1:5173/) and sign in with the account you provisioned. Vite proxies `/api` to port 8080; set `API_PROXY_TARGET` in the frontend terminal if using another backend address. The UI integrates with real product and inventory endpoints. No live Stitch export was supplied. The [catalog design decision](docs/decisions/0002-product-catalog-and-session-access.md) records the previously inspected Figma reference; the [inventory decision](docs/decisions/0003-warehouse-inventory.md) records unavailable inventory previews and reuse of the existing visual system.

On Linux/macOS, use `./mvnw` instead of `.\mvnw.cmd`. Node and Compose commands are the same.

## Validation

With PostgreSQL running, execute `./mvnw -B -ntp verify` from `backend/` (Windows: `.\mvnw.cmd -B -ntp verify`). It runs business unit tests and Failsafe `*IT` tests against real PostgreSQL: migrations, API behavior, validation, sessions/CSRF, organization isolation, competing edits, concurrent receipts, retry deduplication, and rollback on overflow. Fixtures use generated users/passwords and clean up only their own organizations. Use a development/test database, never production. `mvn test` alone does not run the integration suite.

From `frontend/`:

```powershell
npm run lint
npm run test
npm run build
```

Vitest and Testing Library cover catalog/inventory interactions, permissions, loading/empty/error states, forms, stale-edit recovery, and retrying an uncertain stock receipt. The build also checks TypeScript for app and browser-test code. `npm run test:watch` supports interactive testing. `npm run preview` serves a static build locally but does not proxy the backend; full-stack local development uses `npm run dev`.

For critical browser tests, use a separate database named `tradesphere_e2e`. Create it once with `docker compose exec -T postgres createdb -U tradesphere tradesphere_e2e` from the root (substitute your actual PostgreSQL username). Tests bootstrap a fictional test organization/editor there. From `frontend/`, suggested setup commands (execution evidence is recorded separately in the command log):

```powershell
# Use JDK 21 on PATH. Choose and retain a disposable test password across reruns.
$env:E2E_PASSWORD = 'replace-with-a-unique-test-password'
# Optional: use installed Edge, avoiding a separate Chromium download.
$env:E2E_BROWSER = 'msedge'
npm run test:e2e
```

Without `E2E_BROWSER`, install Playwright Chromium with `npx playwright install chromium`. Tests launch their own backend on 8081 and UI on 5174, await readiness, run the real workflows, and stop those processes afterward. Keep these ports free. Reusing the test database requires the original test password because bootstrap never resets credentials. An older TRD-13 test database has no inventory-write grant: use a fresh disposable database or explicitly provision its test account. `E2E_POSTGRES_DB` can select another explicitly disposable test database; never point it at a database containing real users or business data. TRD-15 local verification used `tradesphere_trd15_e2e`.

[TradeSphere CI](.github/workflows/ci.yml) runs backend/PostgreSQL checks, real Chromium catalog/inventory workflows, and frontend install/lint/tests/build on PRs targeting `main` and pushes to `main`. It uses a fresh disposable database and generated database/test passwords, then removes its CI volume. No production deployment is configured. TRD-15 local verification passed 31 backend tests, 22 component tests, and 3 browser tests in Edge. See the [test strategy](docs/testing/TEST_STRATEGY.md), [command log](docs/COMMAND_LOG.md), and [learning journal](docs/LEARNING_JOURNAL.md) for actual evidence and limitations.

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
- [Command log](docs/COMMAND_LOG.md): important executed commands, purpose, flags, results, and troubleshooting.
- [Test strategy](docs/testing/TEST_STRATEGY.md): requirements-derived test coverage and verification policy.
- [Catalog API](docs/api/PRODUCT_CATALOG.md): authentication, requests, pagination, validation, and errors.
- [Inventory API](docs/api/INVENTORY.md): warehouse management, receipt retry behavior, balances, history, and separate write permission.
- [Customer/pricing API](docs/api/CUSTOMERS_AND_PRICING.md): customer fields, exact LKR decimal strings, edit versions, and sales permission provisioning.
- [Order API](docs/api/ORDERS.md): creation/snapshots, lifecycle actions, atomic reservation/release/consumption, audit history, retries, and permission setup.
- [Project context](docs/PROJECT_CONTEXT.md): business scenario, learning goals, and assumptions.
- [Requirements](docs/REQUIREMENTS.md): scope, business rules, and acceptance expectations.
- [Architecture](docs/ARCHITECTURE.md): proposed design, module ownership, and unresolved choices.
- [Development workflow](docs/DEVELOPMENT_WORKFLOW.md): deliverables, review, testing, and decision records.
- [Roadmap](docs/ROADMAP.md): planned sequence and deferred technologies.

All business examples and future demo data represent a simulated organization. Planned capabilities must not be presented as completed work.
