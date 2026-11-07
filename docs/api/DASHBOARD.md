# Operational dashboard — TRD-19

`GET /api/v1/dashboard` requires a signed-in session. Organization scope comes exclusively from that session; there is no organization parameter. Read-only users may access it. Anonymous requests return 401. There is no mutation endpoint.

One read-only repeatable-read transaction returns:

- `counts`: activeProducts, warehouses (all statuses), activeCustomers, recordedBalances, zeroAvailableBalances (recorded rows where available=0), totalOrders (all states).
- `ordersByStatus`: persisted status/total pairs, with omitted states interpreted as zero.
- `recentOrders`: newest five headers, stable created_at/id descending ordering; ID, saved customer name, current status, and creation timestamp.

All queries include organization scope and use existing indexes. The three bounded SQL queries are a deliberate read-only cross-capability projection; mutations remain in their owning services. No extra index or precomputed cache is justified without query evidence. A snapshot is consistent within one response, not continuously updated; refresh obtains a new snapshot.

No quantities are summed across incompatible units. Zero availability includes inactive products/warehouses and fully reserved stock. Unrecorded warehouse/product combinations are excluded. No low-stock threshold, forecast, invented revenue, or general activity stream is introduced. Recent orders link to existing scoped details; status counts link to filtered lists.
