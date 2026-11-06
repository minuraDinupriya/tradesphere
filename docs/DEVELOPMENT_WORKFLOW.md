# Development workflow

Status: Git and TRD-6 application tooling, automated checks, PR template, and GitHub Actions CI are established in the foundation changes. Jira references are maintained manually; no Jira integration or production deployment is configured. Use the [README](../README.md) for runnable commands and the [learning journal](LEARNING_JOURNAL.md) for actual verification evidence.

## One meaningful deliverable at a time

Choose a substantial, outcome-based Jira ticket with a clear scope and observable acceptance criteria. A useful ticket represents a working result, such as receiving stock with validated warehouse balances, rather than separate tickets for every class and file.

For planned feature deliverables, agree on the outcome and arrange a real Jira key before implementation. Do not require an issue key for unrelated automated dependency updates or other authorized changes without a Jira issue. Never invent a key or URL. Keep ticket descriptions concise and outcome-focused; do not create administrative subtasks merely to inflate activity. Unrelated work needs the developer's approval.

## Implementation cycle

1. Read the relevant requirements and acceptance criteria, identify open decisions, and briefly summarize important planned test scenarios before implementation. Consider positive, negative, boundary, authorization, and failure cases; proceed without unnecessary approval checkpoints.
2. Explain significant design choices, alternatives, and tradeoffs in simple English. Resolve consequential business questions before encoding behavior.
3. Use a branch following the conventions below once Git is established; include the real Jira key for Jira-linked work.
4. Implement a small working slice with appropriate automated tests alongside production code. Prefer test-driven development for well-defined business rules where practical. Keep the developer able to follow request flow and business rules.
5. Run available validation appropriate to the change. Inspect the diff for correctness, security, accidental scope changes, and documentation drift.
6. Keep commits small and understandable, with messages describing the resulting behavior.
7. Open a pull request using the title conventions and template below, linking the Jira deliverable when applicable. Describe the problem, resulting behavior, key decisions, executed validation, and remaining limitations.
8. Perform an honest review, address findings, and follow the agreed merge process. Do not invent a reviewer or approval for a solo project.

Current authorization covers TRD-18 lifecycle actions, full fulfillment, eligible cancellation, transactional audit/movements, UI, security, concurrency tests, and documentation. TRD-17 is merged at `a3cb509`. The developer approved locking out cancellation once processing starts and requiring processing before fulfillment; see [decision 0006](decisions/0006-order-lifecycle.md). Shipping, payments, returns, partial fulfillment, backorders, administration, and deployment remain excluded. Preserve the separately stashed mentoring-policy draft. Begin an interactive real-code lesson after implementation, one topic at a time, and quiz before advancing; implementation, verified tests, and demonstrated understanding are separate states. Do not merge the PR or mark TRD-18 Done without developer approval.

## File and code naming

Apply these conventions to new and changed code. Keep established patterns consistent; do not rewrite unrelated files solely for cosmetic compliance.

### Java / Spring Boot

- Packages: lowercase, organized by business capability, such as `catalog` or `inventory`.
- Classes, interfaces, records, and enums: PascalCase, such as `StockReservation`.
- Methods and variables: camelCase, such as `reserveStock` or `warehouseId`.
- Constants: UPPER_SNAKE_CASE.
- Unit test classes: `<ClassName>Test.java`; integration test classes: `<ClassName>IT.java`. Configure the eventual build to discover and run both test types.
- Prefer meaningful business names. Avoid vague `Manager`, `Helper`, or `Utils` names when a specific responsibility can be named.

### React / TypeScript

- React components and component files: PascalCase, such as `OrderDetails.tsx`.
- Hooks: camelCase beginning with `use`, such as `useStockAvailability`; use the same pattern for hook files.
- Functions and variables: camelCase. Types and interfaces: PascalCase.
- Constants: UPPER_SNAKE_CASE when appropriate for fixed shared values; ordinary local values need not use it merely because they are declared with `const`.
- Utility files: camelCase, such as `formatQuantity.ts`.
- Keep related files together in feature-oriented folders. Follow one consistent naming pattern rather than adding competing styles.

### Database

- Tables and columns: snake_case. Prefer plural table names, such as `stock_reservations`, consistently across the schema.
- Use descriptive index and constraint names, such as `idx_orders_organization_id` or `uq_products_organization_sku`.
- Follow Flyway migration naming: versioned SQL migrations use `V<version>__<description>.sql`, for example `V1__create_products.sql`; repeatable SQL migrations, when justified, use `R__<description>.sql`.

### REST APIs

- Use versioned endpoints, such as `/api/v1/products`.
- Resource segments use lowercase plural names; multiword segments use kebab-case, such as `/api/v1/stock-receipts`.
- Use HTTP methods and status codes appropriate to the operation and outcome, such as `GET` for retrieval and `201 Created` for successful resource creation.
- Avoid verbs in resource paths except for meaningful business actions, such as `/api/v1/orders/{orderId}/confirm`. Define action behavior and retry semantics explicitly.

## Git branches and Jira traceability

Do not commit implementation work directly to `main`. For Jira-linked work, use `<category>/<issue-key>-<short-description>`:

- `feature/TRD-8-springboot-foundation`
- `fix/TRD-25-stock-reservation-race`
- `chore/TRD-12-configure-ci`
- `docs/TRD-6-repository-documentation`

These keys are examples only, not evidence that tickets exist. Include the actual issue key in branch names, commit messages, PR titles, and PR descriptions. Add the relevant Jira link in the PR description when its URL is known. Reference related issues when work spans multiple tickets, while keeping the PR focused on one substantial deliverable where possible.

For authorized changes without an issue, use a descriptive branch such as `chore/update-dependencies` and omit the issue suffix from commits and PR titles. Do not create fictitious Jira references to satisfy a format.

## Commit messages and PR titles

Follow Conventional Commits for commit messages and PR titles:

`type(scope): short description (TRD-XX)`

Use a meaningful type, such as `feat`, `fix`, `test`, `docs`, `chore`, `refactor`, `build`, or `ci`, and a scope describing the affected capability or area. Describe actual changes concisely. For changes without a Jira issue, omit the parenthesized key.

Example commit messages:

- `feat(catalog): add product creation API (TRD-24)`
- `fix(inventory): prevent duplicate reservation (TRD-35)`
- `test(ordering): add concurrency tests (TRD-36)`
- `docs(architecture): document module boundaries (TRD-6)`
- `chore(ci): configure backend tests (TRD-12)`

Example PR title: `feat(backend): initialize Spring Boot foundation (TRD-8)`.

All examples are illustrative, not completed work. Avoid generic messages such as “updates”, “fix stuff”, or “final changes”.

## Pull request quality and reviews

Use [.github/pull_request_template.md](../.github/pull_request_template.md). Replace its Jira placeholder with a real key and a known link, or state “Not applicable — no Jira issue.” Adapt example bullets to actual changes; leave checklist items unchecked until satisfied. State when a check or evidence category is not applicable rather than implying it was completed.

Before opening a PR, review the diff, remove debugging artifacts, run applicable automated checks, check for credentials/secrets, ensure the description matches the actual implementation, and update the learning journal for meaningful engineering work. Never claim unexecuted tests passed.

Use professional, specific, actionable review comments. Meaningful feedback should describe the observed concern, why it matters, and a proposed improvement or question. For example:

> Potential race condition: two concurrent requests could read the same available stock before either reserves it. Could we make the check and reservation atomic within a database transaction?

Avoid vague comments such as “bad code” or “fix this”. Do not add unnecessary comments to every PR or fabricate external reviewer comments, approvals, or discussions. Clearly identify solo review as **self-review**.

## Merge and release conventions

Prefer squash merging ordinary feature PRs unless preserving individual commits provides clear value. Use the PR title convention for the squash commit and keep `main` history understandable. Use release tags for meaningful milestones rather than every small feature.

Do not merge automatically. Obtain the developer's approval before merging; preparing a PR or completing self-review does not authorize a merge.

## Testing expectations

Follow the concise [test strategy](testing/TEST_STRATEGY.md). Derive meaningful scenarios from the current Jira feature's requirements and acceptance criteria, rather than generating repetitive manual test-case documents.

- Unit tests should exercise meaningful business rules and invalid cases.
- PostgreSQL integration tests should verify migrations, constraints, rollback behavior, stock reservations, concurrent order transitions, and organization isolation.
- Security tests should cover unauthorized roles and cross-organization reads/writes, including known record identifiers.
- API and frontend tests should cover relevant validation and important user journeys. Choose an appropriate framework when the application exists.
- Frontend component tests must exercise meaningful user interactions. Add end-to-end browser tests for critical complete workflows across the UI, backend, and database.
- Add reusable regression tests for important defects, including a scenario that fails for the defective behavior where practical.
- Concurrency tests should coordinate competing requests/transactions and assert committed outcomes and final balances, rather than rely only on timing sleeps.
- Documentation-only changes need content and link checks; do not add application tests for text edits.

Report what actually ran and its result. Distinguish automated tests, builds, type checks, lint checks, manual checks, skipped checks, and unavailable tooling. An unexecuted or unavailable check is not a pass. Re-run relevant checks after fixes; broaden testing when risk or evidence warrants it.

Run relevant tests before opening a PR. In the PR, document exact commands, results, and uncovered risks. Never invent test results or claim unexecuted tests passed. Routine tests are part of the feature's Definition of Done; do not create separate Jira tickets for them.

## Definition of done for an implementation deliverable

The agreed outcome and acceptance criteria are satisfied; appropriate tests and available checks have run with results recorded; the change has been reviewed honestly; relevant documentation and migrations are included; and unresolved limitations are stated. Meaningful engineering milestones include an automatic [learning journal](LEARNING_JOURNAL.md) update in the same feature branch and pull request, following the [mentoring guidelines](MENTORING_GUIDELINES.md), without a separate routine journal ticket or PR. Do not mark planned work complete based on code generation alone.

Deployment success, user acceptance, and production readiness require their own evidence. The CI workflow exists; report its actual execution status separately from local checks. Production CD is deferred.

## Decision records

For consequential choices, add a short record under `docs/decisions/`. The [engineering foundation decision](decisions/0001-engineering-foundation.md) records the initial TRD-6 choices.

Each record should include a title/date, status (proposed, accepted, or superseded), context, choice, alternatives, consequences, and validation evidence where available. Do not label a proposal accepted without a real decision. Link the relevant requirement, ticket, and pull request when they exist.

Routine implementation details can be explained in the pull request. Revisit architecture when evidence warrants it, updating the affected documents and linking any superseding decision.

## Mentoring and interview evidence

Explain the important concept when it is used, connect it to the current problem, and check that the implementation remains understandable. Capture useful explanations and verified examples without manufacturing project history. The developer should be able to explain why the design works, how it was tested, and where it could fail.

See [agent instructions](../AGENTS.md) for concise operational rules and [roadmap](ROADMAP.md) for sequencing.

## Completion report

After a significant task, report concisely:

- Jira issue key, or that no issue is assigned/applicable.
- Branch name, or that no branch exists.
- Commit summary, distinguishing committed work from uncommitted changes.
- PR title and link when available; otherwise state that no PR was created.
- Exact tests/validation commands executed and their results, including relevant failed or unavailable checks.
- Important technical decisions and tradeoffs.
- Learning journal entry added/updated, concepts to review, and interview questions to practise. If no meaningful engineering milestone occurred, state that no entry was needed.
- Remaining actions, including any pending merge approval.

Keep the report proportional to the task and do not invent missing administrative details.

End every development task with a concise Markdown **Command Execution Report** table containing the exact important commands executed, working directory, purpose/why, actual success/failure, and important errors/resolutions. Group repetitive runs and explain unfamiliar flags where useful. Clearly separate suggested commands from executed commands. For significant milestones, update [COMMAND_LOG.md](COMMAND_LOG.md); include a brief analogy for commands that introduce an important engineering concept.
