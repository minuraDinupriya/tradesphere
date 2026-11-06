# Product catalog API — TRD-13

Base path: `/api/v1`. Same-origin JSON API. The React development server proxies `/api` to the backend. No cross-origin access policy is enabled.

## Authentication and organization boundary

The initial user belongs to one organization and has either catalog read-only or read/write permissions. The backend loads these from PostgreSQL at sign-in; the client cannot choose its organization. All product queries, counts, categories, and updates are scoped to the authenticated organization. Known foreign product IDs return the same 404 as missing IDs. Permissions are a session snapshot; after manually changing a user in this initial system, invalidate their sessions or require sign-out/sign-in. User administration is not implemented.

- `GET /auth/csrf`: public; returns `{ "headerName": "X-CSRF-TOKEN", "token": "..." }` and establishes a session as needed.
- `POST /auth/login`: form-encoded `username` and `password`, with the CSRF header/token from the same session. Returns 204 on success or 401 with a generic failure. Fetch a new CSRF token after successful login.
- `GET /auth/session`: authenticated; returns `username`, `organizationName`, and `canWriteCatalog`. No password hash or client-selectable organization field is returned.
- `POST /auth/logout`: requires the session's CSRF token; returns 204 and invalidates the session/cookie. Obtain a fresh token before another sign-in.

Use the HttpOnly `JSESSIONID` session cookie; do not store authentication tokens in local storage. CSRF protection remains enabled for mutations, including sign-in/out. Sessions expire after 30 minutes of inactivity. Local cookies use SameSite=Lax; deployment must use HTTPS and secure cookies. Login throttling, account recovery, registration, organization switching, and production hardening are deferred.

## Product operations

- `POST /products`: read/write permission. Creates a product, returns 201 with its response body and `Location` header.
- `GET /products/{id}`: read permission. Returns a product or 404.
- `PUT /products/{id}`: read/write permission. Replaces editable fields and requires the last read `version`. Returns the updated product with an incremented version, or 409 for a stale edit/SKU conflict.
- `GET /products`: read permission. Supports `q` (case-insensitive name/SKU substring, max 100), `category` (case-insensitive exact match, max 80), `active` (`true`/`false`, omitted for all), `page` (zero-based, >=0), and `size` (1–100, default 20).
- `GET /products/categories`: read permission. Returns distinct category labels in the current organization's catalog, including inactive products. Does not introduce separate category administration.

List responses contain `items`, `totalElements`, `page`, `size`, and `totalPages`. Results sort by case-insensitive name, then UUID for deterministic ties. A page beyond the result count has an empty `items` list. The list and count share a repeatable-read transaction snapshot. Search treats `%`, `_`, and `!` literally and uses bound SQL parameters, not concatenated user input.

## Editable fields

Example request only; this does not represent seeded production data:

```json
{
  "sku": "BOLT-M8",
  "name": "M8 steel bolt",
  "description": "Fictional demonstration product",
  "category": "Hardware",
  "unitOfMeasure": "Each",
  "active": true
}
```

SKU is 1–64 ASCII letters/numbers/dots/hyphens/underscores and starts with a letter or number. Storage normalizes it to uppercase; a database unique index enforces case-insensitive uniqueness within each organization. The same SKU is permitted in different organizations.

Name, category, and unit of measure are required, with limits of 160, 80, and 32 characters respectively. Description is optional and limited to 2,000 characters; blank descriptions become null. Other text fields are trimmed. `active` must explicitly be a boolean. Category and unit are simple text labels, not a managed taxonomy or conversion system.

For PUT, include all editable fields plus `"version": 0` (using the actual version read from the API). Product responses additionally include `id`, `version`, `createdAt`, and `updatedAt`; timestamps are UTC instants. Never silently retry a stale update with a new version: reload and let the user reconcile edits.

There are no price, tax, stock quantity, image, or deletion endpoints in TRD-13. Active/inactive is catalog metadata; ordering eligibility is not implemented by this ticket.

## Errors

Application errors use `application/problem+json` with `type`, `title`, `status`, and `detail`. Validation errors add an `errors` object keyed by field name. Invalid types, identifiers, pagination values, and request bodies return 400. Anonymous reads return 401; missing permissions or invalid/missing CSRF tokens return 403 (CSRF can reject a mutation before authentication is evaluated). Conflicts return 409. Unknown internal failures use a generic 500 response without internal SQL details.

See [test strategy](../testing/TEST_STRATEGY.md), [catalog integration tests](../../backend/src/test/java/com/tradesphere/catalog/CatalogApiIT.java), and [setup instructions](../../README.md).
