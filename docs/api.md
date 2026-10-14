# API contract

All reconciliation APIs require a session. Anonymous access returns JSON 401.
Missing/foreign runs return identical 404 errors. Unsafe requests, including CLI
uploads and POST logout, require the session's CSRF token from
`GET /api/auth/csrf` in its returned header name. Authentication requires a verified Clerk session token (Bearer header or __session cookie). There is no password API.

Unsafe browser requests require a matching Origin (scheme, host and effective port)
and compatible Fetch Metadata. Cross-origin writes return 403 with code
`CROSS_ORIGIN_REJECTED`. CLI requests require a Clerk Bearer token plus CSRF session cookies/protection.
Routing/protocol errors preserve HTTP 404, 405 (with `Allow`), 406 and 415 rather than
being converted to 500. A 406 response can have an empty body.

## Account and history

`GET /api/me` returns `id`, `displayName`, and `email` for the signed-in user.
`GET /api/auth/config` publicly returns `clerkConfigured`, `publishableKey`, and `frontendApi`. No secret key is exposed.
New Clerk accounts return a fallback displayName and blank email from /api/me; the UI reads profile data from ClerkJS.
`GET /api/auth/csrf` returns `token` and `headerName`; it never returns provider tokens.
`POST /logout` requires CSRF, invalidates the local CSRF session, and returns 204. The UI then calls Clerk.signOut(); a copied JWT stays valid until its short expiry.

`GET /api/reconciliations?page=0&size=20` returns
`{runs: [RunResponse], page, size, totalRuns}`. Size is 1–100; ordering is
created timestamp descending, then UUID descending. Only owned runs are included.

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
