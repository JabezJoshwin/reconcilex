# API contract

## Create a run

`POST /api/reconciliations`, multipart fields `internalFile` and `processorFile`.

Successful response: HTTP 201 and a JSON object with `id` (UUID), `createdAt` (UTC ISO timestamp), `status` (`COMPLETED`), and `summary`.

`summary` contains `internalRows`, `processorRows`, `distinctIds`, and `counts`. Counts always contains all nine keys, including zero values:

`MATCHED`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`, `AMOUNT_AND_CURRENCY_MISMATCH`, `MISSING_IN_PROCESSOR`, `UNEXPECTED_IN_PROCESSOR`, `DUPLICATE_INTERNAL`, `DUPLICATE_PROCESSOR`, `DUPLICATE_BOTH`.

The sum of the counts equals `distinctIds`; the categories are disjoint. A source row count includes duplicate occurrences.

## Retrieve a run

`GET /api/reconciliations/{id}?page=0&size=100`

Page is zero-based. Size is 1–500. Optional `classification` uses one of the exact classification names. Results are ordered by transaction ID.

The response includes the create-response fields plus `findings`, `page`, `size`, and `totalFindings`. The summary always describes the entire run; `totalFindings` describes the filtered set when a filter is supplied.

Each finding contains `transactionId`, `classification`, `internalAmountMinor`, `internalCurrency`, `processorAmountMinor`, `processorCurrency`, `internalOccurrences`, and `processorOccurrences`. Amounts are decimal strings or null. Missing or duplicated sides have null scalar values; occurrence counts distinguish those cases.

## Export and health

`GET /api/reconciliations/{id}/export` returns a downloadable findings CSV containing the entire run, independent of UI pagination and filtering. Formula-looking exported text is escaped for spreadsheet safety.

`GET /api/health` returns `{"status":"UP"}` when the application responds. It does not test database readiness.

## Errors

Errors use `{"code":"...","message":"..."}`. Invalid CSVs, excessive upload limits, malformed IDs, and invalid query parameters return HTTP 400. Unknown run UUIDs return HTTP 404. The application does not return uploaded transaction contents in logs or internal exception details to clients.
