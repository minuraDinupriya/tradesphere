# TRD-16 browser evidence

Actual local Edge screenshots from the final 2026-11-06 [customer/pricing browser workflow](../../../frontend/e2e/customer-pricing.spec.ts), using fictional records in `tradesphere_trd16_e2e`. Inspected after the final permission-label/strict-price-contract changes; these are implementation screenshots, not Figma exports or real customer data.

- [Customers](customers.png): searched customer after editing contact information.
- [Mobile customers](customers-mobile.png): 390px layout with contained table scrolling; automated no-page-overflow assertion passed.
- [Selling price](selling-price.png): persisted LKR 1,234.56, separate price entry, and accurate capability labels. The workflow also rejects 0.001, stores explicit zero, and verifies it after reload.

All four local browser workflows passed, including existing catalog/inventory regression journeys. This is not exhaustive accessibility or cross-browser certification. See [learning journal](../../LEARNING_JOURNAL.md#2026-11-06--trd-16-customer-and-sales-pricing-foundation).
