# Warehouse and inventory API — TRD-15

Implemented endpoints require the [existing session/CSRF flow](PRODUCT_CATALOG.md). Organization and receipt actor come from the authenticated principal, never the JSON body. All authenticated users have organization-scoped inventory read access; warehouse mutations and stock receiving require the separate `inventory:write` authority. Catalog write permission does not imply inventory write permission.

## Warehouses

- `POST /api/v1/warehouses` → 201, `Location`, and warehouse.
- `GET /api/v1/warehouses/{id}` → 200, or 404 for absent/foreign IDs.
- `PUT /api/v1/warehouses/{id}` → 200; include the last retrieved `version`. Stale versions or duplicate codes return 409.
- `GET /api/v1/warehouses?q=&active=true&page=0&size=20` → paginated warehouses. `active` is optional; `q` searches code/name/location, case-insensitively and with literal wildcard characters.

Create body (illustrative fictional values):

```json
{"code":"COLOMBO-01","name":"Fictional Colombo Warehouse","location":"Fictional receiving area","active":true}
```

Code is required, 1–32 letters/numbers/dots/hyphens/underscores, beginning with a letter/number; it is normalized to uppercase and unique within an organization. Name is required (maximum 160 characters), location optional (240), and active required. Updates use the same flat fields plus a required nonnegative `version`. Responses add `id`, `version`, `createdAt`, and `updatedAt`. No deletion endpoint or fixed three-warehouse limit is implemented; business warehouse identities were not supplied.

## Receive stock

`POST /api/v1/inventory/receipts`

```json
{
  "requestId":"11111111-1111-4111-8111-111111111111",
  "warehouseId":"22222222-2222-4222-8222-222222222222",
  "productId":"33333333-3333-4333-8333-333333333333",
  "quantity":20,
  "reference":"FICTIONAL-DELIVERY-1",
  "note":"Fictional example only"
}
```

Replace illustrative IDs with actual same-organization records. Both warehouse and product must be active for a new receipt. Quantity must be a positive integer, maximum **9,007,199,254,740,991**; on-hand balances have the same upper bound. Fractional input is rejected, not truncated. Reference is optional (120 characters), note optional (500); surrounding whitespace is removed and blank optional values become null. Units use the product's catalog unit; there is no conversion.

The client generates a UUID **once per intended receipt** and retains the same payload and ID across retries. A new receipt returns **201**; an identical normalized replay returns **200** with the original movement. Both return `Location: /api/v1/inventory/movements/{id}` and `{ "movement": { ... }, "replayed": false|true }`. Reusing the ID with different warehouse/product/quantity/reference/note returns **409**. Keys are scoped to the organization and retained with movements; no expiration is implemented. The original actor and timestamp remain on replay, even if another authorized user retries it.

The movement and atomic balance increment share one transaction. A failed increment (including supported-range overflow) leaves no movement behind. An existing committed receipt can be replayed after deactivation; replay is not a new delivery. Concurrent identical requests produce one stock effect; concurrent distinct receipts retain both increments. This is a database-backed receipt guarantee, not a claim that unimplemented order workflows are correct.

After a network error or 5xx, the UI keeps the submitted payload locked and offers **Retry same receipt**. Keep that page open until resolved: the retry payload is held in component memory and does not survive a reload/navigation. If you leave, inspect history before recording another delivery. A persistent offline draft/recovery workflow is deferred.

## Balances and movement history

- `GET /api/v1/inventory?q=&warehouseId={uuid}&productId={uuid}&page=0&size=20`
- `GET /api/v1/inventory/movements?q=&warehouseId={uuid}&productId={uuid}&page=0&size=20`
- `GET /api/v1/inventory/movements/{id}` → movement detail; foreign/absent IDs return 404.

Filters are optional. Balance search matches product name/SKU and warehouse name/code. Movement search matches product name/SKU, warehouse code, and receipt reference. Lists for a foreign warehouse/product contain no foreign rows. Invalid UUID syntax is 400. Balance order is product name/warehouse name/IDs; history is newest timestamp/ID first. Each page uses one repeatable-read snapshot for rows/count; later page requests may reflect new data.

All lists use `{items, totalElements, page, size, totalPages}`. Page is zero-based, `page >= 0`, `size` 1–100 (default 20), `q` maximum 100 characters. Parameters are bound and search wildcards escaped.

Balances contain warehouse/product IDs and current labels/status, `onHand`, `reserved`, generated `available`, and `updatedAt`. Only existing balance pairs are listed; untouched combinations are not synthesized as zero rows. Receipts start reserved at zero and do not change it thereafter; there is no reservation API in this ticket.

Movements contain `id`, `requestId`, warehouse/product IDs and current labels/unit, positive `quantity`, `reference`, `note`, `receivedBy` (user ID), `receivedByUsername`, and `receivedAt`. Application endpoints only append/read receipts. Labels reflect current master records; this is not a historical label snapshot or a tamper-proof database audit system.

## Errors and limits

Errors use `application/problem+json` with `status`, `title`, `detail`, and field `errors` when applicable, consistent with the catalog. Common statuses: 400 validation/type errors; 401 no session; 403 missing permission/CSRF; 404 absent/foreign record; 409 duplicate code/stale version/inactive eligibility/request mismatch/overflow; 500 unexpected server failure. Internal traces are not returned.

No order reservations, fulfillment, stock corrections, transfers, procurement, multi-line deliveries, or movement edits/deletion. Warehouse selectors fetch up to 100 records (the fictional scenario has three); product selection searches up to 20 active matches with an instruction to refine search. Production-scale lookup UX is not claimed.

## Permission provisioning

V4 defaults **existing** users to `can_write_inventory=false`. Fresh, explicitly enabled empty-database bootstrap creates an initial catalog/inventory editor. There is no account-administration API.

Suggested owner-only local setup, **not executed against existing development accounts by the agent**: open psql from the root using `docker compose exec postgres psql -U tradesphere -d tradesphere` (substitute your database/user). Inspect `SELECT id, organization_id, username, can_write_inventory FROM tradesphere.users;` and deliberately choose the correct account. Then run:

```sql
UPDATE tradesphere.users
SET can_write_inventory = true
WHERE id = 'replace-with-actual-user-uuid'::uuid
  AND organization_id = 'replace-with-actual-organization-uuid'::uuid
RETURNING id, organization_id, username, can_write_inventory;
```

Verify exactly one intended row changed. Sign out and in because authorities are stored in the session principal. Setting false similarly requires ending existing sessions before treating revocation as effective; centralized session revocation is not implemented. Do not paste password hashes or credentials into a PR.

See [decision 0003](../decisions/0003-warehouse-inventory.md), [test strategy](../testing/TEST_STRATEGY.md), and [verification evidence](../LEARNING_JOURNAL.md#2026-11-06--trd-15-warehouse-and-inventory-operations).
