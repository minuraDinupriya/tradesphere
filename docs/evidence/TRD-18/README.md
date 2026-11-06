# TRD-18 browser evidence

Actual local Edge screenshots from the 2026-11-06 [lifecycle browser test](../../../frontend/e2e/order-lifecycle.spec.ts), using fictional isolated records in `tradesphere_trd18_e2e`. These are implementation screenshots, not Figma exports. Desktop fulfilled/dialog and 390px mobile screens were inspected; horizontal table content remains contained rather than expanding the page.

- [Cancelled order](cancelled-order.png): confirmed cancellation releases both reservations, preserves physical stock, and records one movement per item.
- [Fulfillment dialog](fulfillment-dialog.png): processing order, safe Go back focus, explicit physical-stock/irreversibility explanation.
- [Fulfilled order](fulfilled-order.png): a committed response was deliberately dropped; retry reports replay, with four transitions and two movements rather than duplicate effects.
- [Mobile lifecycle](lifecycle-mobile.png): persisted fulfilled state, scoped history, contained table scrolling, and automated no-page-overflow assertion at 390px.

The test also cancels a pending order without stock effects, verifies Escape restores focus without sending the action, checks both cancellation balances, and verifies final on-hand/reserved/available values of 8/0/8 and 17/0/17. All six local Edge workflows passed after narrowing the older Pending assertion to its status badge. This is not exhaustive accessibility/browser/load/failover certification. Hosted results are recorded separately in the [journal](../../LEARNING_JOURNAL.md) and PR checks.
