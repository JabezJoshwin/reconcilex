# 60–90 second demo

Configure Clerk authentication using docs/authentication.md, then start the application
using the README before recording. Open http://localhost:8080
or the configured `APP_PORT`; this checkout's running demo is http://localhost:18080.

1. **0–15 seconds:** Explain: “ReconcileX compares internal payment records to processor records using the exact shared payment reference. It handles integer minor units and keeps every duplicate occurrence.”
2. **15–30 seconds:** Upload `sample-data/clean-internal.csv` and `sample-data/clean-processor.csv`. Submit and show three matches even though processor rows are in a different order.
3. **30–55 seconds:** Upload the two `mixed-*.csv` files. Show the nine disjoint classifications, including duplicate ambiguity, missing payments, and amount/currency discrepancies. This pair has 11 internal rows, 10 processor rows, and 10 distinct IDs.
4. **55–70 seconds:** Download the findings CSV. Point out that the formula-looking ID is escaped in the report and duplicate occurrence counts remain visible.
5. **70–90 seconds:** Open the run from your dashboard history and explain that it is persisted atomically and restricted to your account. Log out. Mention meaningful parser/matcher/API/Clerk token security tests and the boundary: “This is a payment-reference reconciliation MVP, not a certified production bank reconciliation service.”

Expected mixed counts: `MATCHED=1`, `AMOUNT_MISMATCH=1`, `CURRENCY_MISMATCH=1`, `AMOUNT_AND_CURRENCY_MISMATCH=1`, `MISSING_IN_PROCESSOR=2`, `UNEXPECTED_IN_PROCESSOR=1`, `DUPLICATE_INTERNAL=1`, `DUPLICATE_PROCESSOR=1`, `DUPLICATE_BOTH=1`.

For an error demo, upload `invalid-amount.csv` or `invalid-currency.csv` as either side and show the HTTP 400 feedback. Upload `empty.csv` on both sides to demonstrate a valid empty reconciliation.
