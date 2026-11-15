# 0008 — Reconcile recorded stock with a physical count

Status: implemented on the [TRD-20 feature branch](https://minuraranaweera.atlassian.net/browse/TRD-20).

An inventory writer submits a counted on-hand quantity for a recorded balance and a reason. We use an absolute count because that is what the operator observes. A signed delta could preserve unrelated stock increments, but calculating it from a stale count can apply the wrong correction. Comparing the submitted expected on-hand with the locked current quantity catches a changed balance. A pair of intervening changes that return the quantity to its original value can still pass this check. The endpoint does not track movement since the physical count started, so staff need a current count.

Inventory acquires the existing warehouse/product eligibility locks before locking the balance row. On the latest row it validates both the expected count and the reserved-stock floor. Order reservations, fulfillment, receipts, and adjustments serialize their writes on that row. The separate adjustment ledger uses organization-aware foreign keys and a unique organization/request key. The ledger and balance update share one transaction.

The new operation uses the existing inventory-write permission and existing active product/warehouse rules. An exact retry returns the original entry. Reason and original actor are recorded, while the displayed username comes from the current user record. Receipt and order histories retain their individual semantics. See the [API contract](../api/INVENTORY.md#stock-adjustments-trd-20) and [learning journal](../LEARNING_JOURNAL.md) for validation.
