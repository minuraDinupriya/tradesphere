# Repeatable fictional interview demonstration

Use an isolated database and organization with deliberately provisioned permissions. Do not use real customer data or reset shared data to prepare a demo.

1. Sign in; explain authentication versus organization-bound authorization.
2. Create two hardware products and a warehouse, then receive 10 bolts and 20 washers. Show on-hand/reserved/available.
3. Create a fictional customer. Set exact LKR prices, explaining unpriced versus zero and decimal strings.
4. Create an order for 2 bolts/3 washers. Explain immutable price snapshots. Confirm: balances become 10/2/8 and 20/3/17.
5. Process and fulfill: balances become 8/0/8 and 17/0/17. Show state history and two consumption movements.
6. Create/confirm another order, then cancel. Explain why only reserved falls and why retries cannot release twice. Processing cancellation is forbidden.
7. Open Dashboard, refresh, compare totals/status filters with Orders. Explain recorded zero-availability balances without claiming a low-stock threshold.
8. Demonstrate invalid input and an insufficient-stock order, then explain rollback tests and tenant isolation using real test files. Do not deliberately corrupt demo data.
9. Sign out and show protected API denial. Discuss honest limits: no payment, shipping, returns, partial fulfillment, general audit retention, or deployment certification.

Review one topic at a time: request flow, transaction/conditional SQL, state machine/replay, permissions, consistent dashboard reads, then Docker/CI. Ask the learner to explain each concept before claiming understanding.
