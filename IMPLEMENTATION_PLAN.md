# Implementation contract

1. Pure CSV parser and deterministic matcher; each distinct ID has exactly one classification.
2. Main agent persists summaries, findings and source rows atomically using JDBC and Flyway.
3. Multipart API, bounded retrieval, spreadsheet-safe export, static dashboard.
4. Focused tests, integration tests, real PostgreSQL smoke check where available, review.

Java 21; Spring Boot 3.5.6; Commons CSV 1.14.1. Engine interfaces:
`new CsvTransactionParser().parse(InputStream input, String source)` returns `List<TransactionRow>`;
`new ReconciliationEngine().reconcile(List<TransactionRow> internal, List<TransactionRow> processor)` returns `List<Finding>`.
Parser rejects over 5*1024*1024 bytes and 20,000 data rows; strict UTF-8 and exact ordered header.
Findings sorted by transactionId using Java natural order. Duplicate sides have null scalar amount/currency; all original rows separately persisted.

POST /api/reconciliations: multipart internalFile and processorFile; 201 RunResponse.
GET /api/reconciliations/{uuid}?page=0&size=100&classification=MATCHED: RunDetails; size 1..500; optional enum filter.
Summary: internalRows, processorRows, distinctIds, counts (all nine classification keys including zero).
RunResponse: id, createdAt (ISO UTC), status COMPLETED, summary.
RunDetails adds findings, page, size, totalFindings (filtered count).
Finding: transactionId, classification, internalAmountMinor, internalCurrency, processorAmountMinor,
processorCurrency, internalOccurrences, processorOccurrences. Amounts serialized as decimal strings to preserve
signed-64-bit precision in JavaScript. Error: {code,message}; 400 input errors; 404 missing run.
GET /api/reconciliations/{uuid}/export downloads ALL findings, independent of pagination/filter.
GET /api/health: {status:UP}; application liveness, not database readiness.
