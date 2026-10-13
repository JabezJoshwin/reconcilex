# Architecture and decisions

ReconcileX is one Spring Boot application with a static browser dashboard and a PostgreSQL database. The scope is exact reconciliation of a shared payment reference between two transaction lists. It does not reconcile bank payouts or aggregate settlements.

## Processing path

1. The multipart controller accepts the two named files and validates transport limits.
2. The core parser validates bytes, UTF-8, the ordered CSV header, row limits, IDs, positive signed-64-bit amounts, and ISO 4217 currencies. Both inputs must be valid before persistence begins.
3. The core matcher indexes both lists by exact transaction ID. Each distinct ID produces exactly one finding, sorted using Java string natural order.
4. A service transaction writes the completed run, summary, findings, and original source rows. A write failure rolls back the run, preventing successful runs with partial findings.
5. Retrieval pages persisted findings. Export reads all findings for the run and does not depend on the current page or filter.

The core has no HTTP or database dependency. Controllers handle transport, the service coordinates the run, and JDBC repositories handle parameterized database operations. Flyway versions the schema. JDBC keeps the persistence path explicit without an additional ORM layer.

## Matching and duplicates

Duplicate classification has precedence over amount, currency, and presence checks. More than one occurrence internally and in the processor yields `DUPLICATE_BOTH`; otherwise the repeated side determines `DUPLICATE_INTERNAL` or `DUPLICATE_PROCESSOR`. The counterpart may be absent. A duplicate cannot produce a successful match.

For nonduplicates, missing sides are classified first, followed by the four amount/currency combinations. There is no normalization or fuzzy matching of payment IDs. IDs are compared case-sensitively, with surrounding spaces preserved. The hash indexes give expected O(n + m) matching work; deterministic sorting adds O(k log k), where k is the number of distinct IDs. Space is O(n + m).

Finding amounts and currencies are null for ambiguous sides; occurrence counts retain the ambiguity. All original source rows are persisted separately, so repeated values are never silently discarded. The findings export reports the classification and counts rather than pretending one duplicate row represents the group.

Amounts use Java `long` and PostgreSQL `BIGINT`. JSON represents amounts as decimal strings because JavaScript numbers cannot preserve every signed-64-bit integer. No currency conversion, rounding, totals across currencies, or floating-point money operations are needed.

## Safety and limits

- Each file is limited to 5 MiB (5 × 1024 × 1024 bytes) and 20,000 data rows. Header-only CSVs are valid.
- Required headers are exactly `transaction_id,amount_minor,currency` in that order. Malformed data is rejected as a whole, with HTTP 400.
- ID length is capped at 128; blank IDs are rejected. Amounts must be strictly positive signed-64-bit integers. Currencies must be valid ISO 4217 codes.
- CSV output quotes fields correctly and neutralizes spreadsheet formula prefixes in user-controlled fields. The persisted ID remains unchanged.
- Browser transaction values are inserted through `textContent`. Database statements use parameters. Upload data is never treated as a path.
- Secrets come from the environment; local Compose ports bind to loopback. The demo has no authentication and must not be publicly exposed without it.

## Deliberate tradeoffs

Files are parsed in memory within strict bounds. This avoids introducing a queue or staging system for a two-day MVP. Matching and persistence are synchronous; large valid requests can take longer, but the caller receives a completed report or an error.

Every successful POST creates a new UUID. Repeated identical files produce the same classifications and counts but a different run ID and timestamp. The API does not promise idempotent submission or exactly-once semantics.

The health endpoint reports application liveness, not database readiness. H2 integration tests exercise the HTTP/service/persistence path cheaply; a PostgreSQL smoke check is required to validate PostgreSQL-specific behavior. No performance numbers should be inferred from unit tests.

Authentication, authorization, tenant isolation, retention policies, audit logs, asynchronous jobs, payout matching, foreign exchange, settlement fees, and production compliance are outside this MVP.
