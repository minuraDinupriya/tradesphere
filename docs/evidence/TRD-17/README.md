# TRD-17 browser evidence

Actual local Edge screenshots from the 2026-11-06 [order browser workflow](../../../frontend/e2e/ordering.spec.ts), using fictional isolated records in `tradesphere_trd17_e2e`. These are implementation screenshots, not Figma exports or real customer records. Desktop/create/confirmed/mobile screens were inspected with no material layout issue found.

- [Create order](create-order.png): one customer/warehouse, two products, whole-unit quantities, and reference catalog prices.
- [Pending order](pending-order.png): immutable snapshot total LKR 25.30 with no stock reserved yet.
- [Confirmed order](confirmed-order.png): after the server committed but its response was dropped, same-order retry reports the saved confirmation without another reservation.
- [Mobile detail](order-mobile.png): 390px layout; contained table scrolling and automated no-page-overflow assertion.
- [Reserved inventory](reserved-inventory.png): physical/reserved/available values 5/2/3 and 8/3/5 after confirmation/retry.
- [Order list](orders.png): scoped search and confirmed state.

The test changes a master price after pending creation and verifies the saved total/price remain unchanged. All five local browser workflows passed after correcting an ambiguous customer test selector. This is not exhaustive accessibility, browser, load, or failure recovery certification. See the [journal](../../LEARNING_JOURNAL.md#2026-11-06--trd-17-order-creation-and-atomic-reservation) and PR checks for actual hosted evidence.
