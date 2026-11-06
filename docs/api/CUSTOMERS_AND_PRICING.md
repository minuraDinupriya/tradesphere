# Customer and sales pricing API — TRD-16

Uses the existing [session and CSRF flow](PRODUCT_CATALOG.md). Organization is always taken from the server principal. All signed-in users can read customers and prices within that organization. `customers:write` and `prices:write` independently authorize mutations; catalog/inventory write permissions grant neither.

## Customers

- `POST /api/v1/customers` → 201, customer body and `Location`.
- `GET /api/v1/customers/{id}` → 200; absent/foreign IDs → 404.
- `PUT /api/v1/customers/{id}` → 200; requires the retrieved `version`; stale edits → 409.
- `GET /api/v1/customers?q=&active=true&page=0&size=20` → `{items,totalElements,page,size,totalPages}`.

Illustrative create body (fictional only):

```json
{"name":"Fictional Hardware Store","contactName":"Fictional Contact","email":"contact@example.test","phone":null,"address":"Fictional address","active":true}
```

Required name: nonblank, maximum 160 characters. Optional contactName: 160; email: valid email syntax, 254; phone: 40; address: 1000. Active is required. Text is trimmed, blank optional values become null. Phone is a length-limited contact label, not verified ownership/number validation. Names/contact details are not unique. Responses contain UUID `id`, fields above, `version`, `createdAt`, and `updatedAt`; update accepts the same flat editable fields plus nonnegative integer `version`.

Search matches name/contact person/email/phone case-insensitively; `%`/`_` are literal characters. Optional active filter, stable name/ID ordering, zero-based page >= 0, size 1–100 (default 20), q maximum 100 characters. List rows/count share one repeatable-read snapshot; separate pages can shift under edits. No deletion, portal, orders, credit, or invoices.

## Product selling price

- `GET /api/v1/products/{id}/selling-price` → `{productId,currency:"LKR",amount:null|"1234.56",version}`.
- `PUT /api/v1/products/{id}/selling-price` with `{"amount":"1234.56","version":0}` → updated price, 200.
- Existing product list/detail responses now also expose nullable decimal-string `sellingPrice`.

The amount must be a **JSON string**, from `"0"` through `"999999999999.99"`, with at most 12 whole digits and two decimal places. Negative values, extra decimals (even trailing zeros beyond two), exponent/grouped notation, blank/null, JSON numbers, and overflow are rejected with 400; no automatic rounding. Responses always use two decimal places. React retains strings and never converts money through `Number` or `parseFloat`. [TRD-17 orders](ORDERS.md) snapshot this value at creation; changing the catalog price does not reprice those saved orders.

One price applies to the organization's product across warehouses, per its catalog unit. New/existing products start unpriced (`amount:null`, price version 0); unpriced is different from explicitly free (`"0.00"`). There is no clear-price operation after setting it in this slice. Product active status does not restrict price maintenance; order eligibility is deferred. Currency is server-owned LKR, not a configurable payload field.

Price edits compare/increment a separate `price_version` atomically. Stale edits return 409; missing/foreign products return 404. Catalog edits never write price columns, even if an extra price property is submitted; price edits leave catalog version/timestamps unchanged. The current price has no history/timestamp API in this foundation. Future orders must define their own price snapshot semantics; none are implemented here.

Database uses PostgreSQL `numeric(14,2)` and Java `BigDecimal`; API validation occurs before SQL because PostgreSQL typemods can round direct SQL inputs. The no-rounding promise applies to this API; privileged direct database writes bypass application validation. Taxes, payments, multi-currency, invoices, credit, discounts/price lists, and ordering remain excluded.

## Errors and permission provisioning

Errors use shared `application/problem+json` (`status`, `title`, `detail`, optional field `errors`). 400 invalid body/type/fields/parameters; 401 unauthenticated; 403 permission/CSRF denial; 404 absent/foreign identifier; 409 stale version; 500 unexpected failure without internal traces. Frontend retains edits on failure and offers explicit latest-version reload, which discards local input.

V5 defaults existing users' `can_write_customers` and `can_write_prices` to false. Explicit fresh-empty-database bootstrap creates the first editor with both flags; databases with users are left alone. No existing development account was granted permissions by the agent. Suggested owner-only SQL, **not executed on existing accounts**:

```sql
UPDATE tradesphere.users
SET can_write_customers = true, can_write_prices = true
WHERE id = 'replace-with-actual-user-uuid'::uuid
  AND organization_id = 'replace-with-actual-organization-uuid'::uuid
RETURNING id, username, organization_id, can_write_customers, can_write_prices;
```

Select only the permissions intended for the chosen account, verify the returned row, and sign out/in. Session authorities are snapshots; immediate centralized revocation is not implemented. Full role administration remains out of scope. See [decision 0004](../decisions/0004-customers-and-sales-pricing.md) and [learning journal](../LEARNING_JOURNAL.md).
