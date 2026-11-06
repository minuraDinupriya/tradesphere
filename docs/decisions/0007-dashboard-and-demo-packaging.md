# 0007 — Operational projection and same-origin demo

2026-11-06, TRD-19. Status: local implementation; public hosting/release approval pending. Based on merged TRD-18 `a31044b`.

Dashboard owns a read-only projection. Three bounded JDBC queries share a repeatable-read transaction, with the authenticated organization on every source. This avoids per-record API calls and inconsistent count/list snapshots without caches/reporting infrastructure. Existing organization/created-at indexes support the selected access pattern; no benchmark or load guarantee is claimed. Metric definitions are explicit in the [API](../api/DASHBOARD.md); no arbitrary thresholds or incompatible-unit totals.

One non-root Java runtime serves React/API from one origin, preserving session/CSRF behavior. Separate Docker build stages isolate Node/Maven; `.dockerignore` excludes local secrets. Demo Secure cookies require HTTPS ingress that overwrites forwarding headers; PostgreSQL remains separate/persistent. Split hosting would add origin/cookie configuration without a current benefit. Single-instance sessions are lost on restart; this is a demo limitation, not high availability.

Reuse the established visual system and real navigation, with no UI framework or broad redesign. Container CI verifies packaged routing/readiness/API denial separately from application tests. [Deployment](../DEPLOYMENT.md) compares costs and approval boundaries. No live deployment or formal security certification is inferred from code/CI.
