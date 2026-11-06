# 0004 — Customer and sales pricing foundation

Date: 2026-11-06 (Asia/Colombo). Status: implemented on the TRD-16 branch; review/merge pending. [Actual Jira scope](https://minuraranaweera.atlassian.net/browse/TRD-16) depends on merged TRD-13. Current base `3f49db4` also contains merged TRD-15. No Jira status/comment was changed by the agent.

## Approved scope

The developer approved customer name, optional contact person/email/phone/address, active status, UUID IDs, no forced name/contact uniqueness or deletion; optional initial LKR prices with zero allowed, at most two decimals, exact decimal storage/API strings, and no silent rounding; and independent customer/price write permissions with no automatic grants to existing users. Taxes, real payments, invoices, credit, multi-currency, and ordering are excluded.

## Choices, alternatives, and tradeoffs

- Keep customers in `ordering`, following the existing provisional module ownership. This creates no order workflow. A separate Customer capability can be extracted when behavior warrants it; another deployment would add no current value.
- Retain JDBC records, explicit repositories, service transactions, parameterized tenant predicates, bounded literal search, and version-checked edits. Responses omit organization authority; controllers derive it from `OrganizationUser`. Repeatable-read aligns list/count within a response, not across pages.
- Store one nullable `selling_price numeric(14,2)` on the organization-owned product. A separate prices table would add joins/lifecycle without current price lists, currencies, or history. NULL means unpriced; zero is an explicit price. There is no price clearing endpoint in the approved initial workflow.
- Use `SellingPriceAmount`/`BigDecimal` constructed from text, never double; enforce nonnegative values and the documented precision before persistence. PostgreSQL checks reinforce nonnegative/non-NaN storage. `numeric(14,2)` alone would round extra decimals, so the API rejects them first. Decimal strings preserve exactness across JSON/TypeScript, with a scoped deserializer rejecting numeric-token coercion and a string formatter for display. Integer minor units were an alternative but would require a different API/unit contract.
- Use independent price version checks on the product row. Catalog SQL never writes price columns; price SQL never writes catalog metadata/version. This permits independent permissions and avoids stale catalog edits overwriting prices. It does not provide price history; future orders need explicit snapshot rules. Whole-row database locks may briefly contend with other updates on the same product.
- Identity adds `customers:write` and `prices:write` flags/authorities. Existing rows default false; explicit fresh bootstrap provisions an initial editor. This extends the existing small capability-permission approach rather than invent a full roles/admin system. Sign-in refreshes permission snapshots; immediate revocation remains deferred.
- Move the existing generic Inventory request hook to shared `useRemoteData`, then reuse it for customer/pricing reads. This avoids Customer importing Inventory UI internals and adds no dependency. Keep API calls separate from presentation and error/permission controls consistent with existing screens.

## Design review

Read the supplied Figma reference guidelines and existing catalog/inventory decisions. Preserve the established light canvas, white sidebar, purple actions, compact tables/forms, labels, linked errors, loading/empty/retry/success states, explicit stale-edit reload, and contained mobile table scrolling. The prior Make preview access limitation remains; customer-specific design fidelity was not verified, and no unavailable screen details or generated code were copied. Exclude financial summaries, credit, invoices, payment/order actions, fake metrics, and role administration. Price editing is a separate permission-controlled form so catalog-only editing cannot accidentally change money.

## Validation and limits

Unit tests cover exact decimal arithmetic, formatting boundaries, invalid values, and no rounding. PostgreSQL/API tests cover persistence, validation, customer edits/search, tenant boundaries, separate permissions/CSRF, catalog price tampering, and simultaneous price edits. Frontend tests cover meaningful customer/price interactions and failures; a real browser journey creates/edits a customer and sets/validates/reloads a price. Existing catalog/inventory workflows remain regression coverage. Actual commands/results and hosted status are in the [journal](../LEARNING_JOURNAL.md) and [command log](../COMMAND_LOG.md), not inferred from this decision.

No new framework, production deployment, audit platform, generalized currency abstraction, price history, complex phone verification, or customer uniqueness rule is introduced. [API details](../api/CUSTOMERS_AND_PRICING.md) document the representation bound and permission provisioning.
