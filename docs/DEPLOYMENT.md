# Demonstration deployment — proposal, not deployed

Status: **the developer chose to keep deployment local on 2026-11-06** after reviewing the paid Render proposal. No provider resource has been created, no public URL verified, and no release published. Remote deployment is deferred by that decision, not claimed complete. Future spending, provisioning, or public exposure requires new explicit authorization.

## Minimal architecture

One Docker image serves React assets and the Spring Boot API from one HTTPS origin; a separate PostgreSQL database persists data. This avoids cross-origin session/CSRF complexity. The non-root runtime contains Java 21, while Node/Maven exist only in build stages. Docker packaging skips tests intentionally: full verification must pass separately in CI before release.

The `demo` Spring profile requires DATABASE_JDBC_URL, DATABASE_USERNAME, DATABASE_PASSWORD. Use a JDBC PostgreSQL URL with the provider's required TLS configuration, not an HTTP URL. The pool is limited to five connections. Set SERVER_PORT to the hosting ingress port. The image listens on all interfaces inside the container; only HTTPS ingress should be public. PostgreSQL must remain private or tightly restricted.

Secure, HttpOnly, SameSite=Lax session cookies and backend CSRF remain enabled. Forwarded headers are trusted only behind a controlled ingress that overwrites client-supplied headers; never expose the app container directly to the internet. The demo profile serves only explicit frontend routes; `/api/v1/**` retains backend authorization. Health readiness is `/actuator/health/readiness` without internals. Single-instance in-memory sessions are intentional; restart signs users out. No distributed sessions or zero-downtime guarantee.

## Options researched 2026-11-06

- **Render Free:** $0 eligible compute, managed TLS, but idle web instances sleep after 15 minutes and may take about a minute to wake. Free PostgreSQL expires after 30 days; therefore unsuitable for preserving an interview demo through January 2027. Usage allowances and restarts still apply. [Official free-service limitations](https://render.com/docs/free).
- **Railway Hobby:** $5/month minimum subscription including $5 resource usage; usage above the allowance increases the bill. Container app plus persistent PostgreSQL is operationally simple, but $5 is not an all-inclusive quote for both services. Review measured memory/CPU/storage/egress and spending controls before approving a budget. [Official pricing](https://docs.railway.com/pricing), [billing](https://docs.railway.com/pricing/understanding-your-bill).

Recommended proposal: Render Hobby workspace ($0), Starter container web service ($7/month, 512 MB), Basic-256mb PostgreSQL ($6/month) plus 1 GB database storage ($0.30/month): **$13.30/month USD baseline**, before tax, exchange/card fees, bandwidth/build overages, or extra storage. This is a quote model from current official published material, not a guaranteed final invoice; confirm checkout before provisioning. A $15/month pre-tax planning budget leaves little overage headroom and is not a provider-enforced cap. Do not enable upgrades or overage spending without owner agreement. [Official service price comparison](https://render.com/articles/best-netlify-alternatives-in-2026), [storage/allowances/billing](https://render.com/articles/how-much-does-cloud-application-hosting-cost-for-small-businesses).

This single-provider option provides persistent PostgreSQL, private connectivity, and managed TLS with predictable base compute, while Railway's resource bill needs measurement. The tradeoff is higher baseline cost than an intermittent trial. Free Render is a disposable preview alternative only. Cloud credits are optional, not prerequisites. Kubernetes/multi-cloud remain excluded. Approval of a named plan and budget is still required; none has been requested or inferred yet.

## Deployment and recovery checklist (not executed remotely)

1. After owner approval, create a private PostgreSQL service and container web service. Store credentials in provider secrets, never Git/build arguments. Verify persistent storage, backups, budget alerts/limits, region, TLS, and billing before enabling traffic.
2. Build the reviewed commit with `docker build -t tradesphere:<version> .`. Configure demo profile and environment variables. Check readiness; Flyway must finish successfully. Never rewrite applied migrations or use `clean` on a persistent database.
3. Bootstrap only an empty demo database with an explicitly chosen fictional organization/editor/password. Disable bootstrap and remove its password after setup. Never publish credentials in screenshots/logs. This MVP has no public registration or user administration.
4. Verify HTTPS, Secure/HttpOnly/SameSite cookies, sign-in/out, CSRF rejection, anonymous API denial, scoped dashboard, and the complete [demo guide](DEMO_GUIDE.md). Restart the service and verify database persistence. Do not claim deployed until these checks actually run.
5. Redeploy a reviewed commit only after CI; a restart invalidates sessions. For migration failure, inspect sanitized logs and migration history, restore from a tested backup into a separate database if needed, and reconcile before retry. Do not blindly roll back application code across an incompatible schema.
6. Before a demonstration, check readiness, provider status/budget, database backup, and fictional fixtures. For 401 sign in again; for 403 refresh the session/security token; for 409 reload current data; for 5xx inspect sanitized logs and database connectivity. Retry only with the original idempotency identity where supported.

Backup/restore procedures and any provider-specific commands must be validated against the selected provider before a live release; this document is not evidence of disaster recovery testing.
