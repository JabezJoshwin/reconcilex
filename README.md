# ReconcileX

A payment reconciliation MVP built with Java 21, Spring Boot, Maven, PostgreSQL, Apache Commons CSV, and a browser dashboard served by the same application.

[Open the hosted demo](https://reconcilex-demo.onrender.com) · synthetic data only.
Free hosting sleeps when idle; the first visit may take a minute or longer.

Upload internal and processor CSVs to compare the exact shared payment reference, inspect disjoint findings, retrieve a persisted run, and export a spreadsheet-safe report. Reconciliation uses positive integer minor units; it never uses floating-point money arithmetic.

## Run locally

Requirements: Docker with Compose. For development and tests, install a JDK 21; the Maven wrapper downloads Maven and dependencies on first use.

Create a root `.env` file containing a strong local database password:

```dotenv
DB_PASSWORD=replace_with_your_own_local_password
```

Do not commit `.env`. The repository's `.env.example` is a template, not a credential.
If ports 5432 or 8080 are already in use, set `DB_PORT` or `APP_PORT` in `.env`.
Use the selected application port in browser/curl URLs and the selected database port in `DB_URL`
when running Java locally. This checkout's verified demo uses `DB_PORT=55432` and `APP_PORT=18080`.

From the repository root:

```sh
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/api/health
```

Configure Clerk authentication using [authentication setup](docs/authentication.md), then
open http://localhost:8080/signup. After signing in, the private dashboard shows
your previous runs and the upload workspace. Select both `sample-data/clean-*.csv`
files for three matches; try the mixed fixtures for all nine classifications.
Without Clerk configuration, login is unavailable and no reconciliation API is anonymous.

Compose stores data in a PostgreSQL volume and binds the application and database ports to localhost. Stop the demo with `docker compose down`; this preserves the database volume. Public deployment requires the HTTPS, proxy, rate-limit and operational release gates in the authentication guide.

For development, run only PostgreSQL in Compose, then start Spring Boot using the same password:

```sh
docker compose up -d db
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_PORT:-5432}/reconcilex"
export SERVER_PORT="${APP_PORT:-8080}"
cd backend
./mvnw spring-boot:run
```

The optional configuration variables are `DB_URL` (default `jdbc:postgresql://localhost:5432/reconcilex`) and `DB_USER` (default `reconcilex`). Do not place credentials in source files.

## Test and package

Free-tier CI/CD and hosted demo setup: [deployment guide](docs/deployment.md).
The explicit `demo` profile permits HTTPS hosting with Clerk development keys;
it does not relax production profile authentication requirements.

From `backend/`:

```sh
./mvnw test
./mvnw package
```

The default suite uses H2 for API integration tests and does not need Docker. Unit tests cover CSV validation and matching, including duplicates, empty inputs, and deterministic output. API tests cover persistence/retrieval, transaction rollback, upload failures, malformed quoting, and safe CSV export. GitHub Actions runs on Java 21 with a PostgreSQL service and enables the PostgreSQL test; the remote workflow has not been executed in this session.

Authentication tests use real signed Clerk-shaped JWTs to verify signatures and
claims, CSRF, ownership, expiry/logout, production cookie flags, and rejection of
legacy Google sessions. Live Clerk signup/login requires your configured instance. Frontend checks:

```sh
node --test backend/src/test/frontend/*.test.cjs
```

An opt-in test exercises the full workflow against a running PostgreSQL instance. From the repository root, set the connection variables for your local test database and run:

```sh
RECONCILEX_POSTGRES_TEST=true \
  DB_URL=jdbc:postgresql://localhost:5432/reconcilex \
  DB_USER=reconcilex DB_PASSWORD='your_local_password' \
  ./backend/mvnw -f backend/pom.xml -Dtest=PostgresWorkflowTest test
```

This test persists a run; use a local demo/test database.

Execution evidence and infrastructure limitations are recorded in [verification.md](docs/verification.md). No benchmark claims are made without a measured run.
The [senior backend review](docs/review-20261013.md) records confirmed findings, reproductions,
targeted security/API fixes, and subsequent verification.

The optional synthetic benchmark parses and reconciles 10,000 rows per file, checks 9,000 matches
and 1,000 amount mismatches, and reports five timings after three warmups:

```sh
./backend/mvnw -f backend/pom.xml -Dtest=BenchmarkTest -Dreconcilex.benchmark=true test
```

It excludes database, HTTP and export time. The subsequent review run measured a 28.233 ms median
on Apple M1 / Java 21.0.1; this is a local diagnostic, not a production performance claim.

## API walkthrough

From the repository root while the app is running, use a fresh Clerk session token
in CLERK_SESSION_TOKEN plus CSRF cookies in a local cookies.txt file and a fresh
CSRF_TOKEN from GET /api/auth/csrf. Tokens expire quickly; prefer the browser UI.
Treat tokens and cookies as secrets; never commit or share them.
The browser dashboard handles these automatically. The following CLI examples
assume you have securely obtained that session; they no longer work anonymously.

```sh
curl --fail -i \
  -b cookies.txt -H "Authorization: Bearer $CLERK_SESSION_TOKEN" -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
  -F internalFile=@sample-data/mixed-internal.csv \
  -F processorFile=@sample-data/mixed-processor.csv \
  http://localhost:8080/api/reconciliations
```

Copy the returned UUID and replace `RUN_ID` in the following commands:

```sh
curl --fail -H "Authorization: Bearer $CLERK_SESSION_TOKEN" 'http://localhost:8080/api/reconciliations/RUN_ID?page=0&size=100'
curl --fail -H "Authorization: Bearer $CLERK_SESSION_TOKEN" \
  http://localhost:8080/api/reconciliations/RUN_ID/export \
  --output findings.csv
```

The mixed fixtures contain 11 internal rows, 10 processor rows, and 10 distinct IDs. Counts are one per classification except `MISSING_IN_PROCESSOR`, which has two. One ID deliberately begins with `=` to demonstrate spreadsheet formula escaping during export.

The [API contract](docs/api.md) documents DTO fields, pagination, filters, and errors. Monetary JSON fields are decimal strings so the browser preserves signed-64-bit precision.

## CSV format and behavior

```csv
transaction_id,amount_minor,currency
pay_001,50000,INR
pay_002,120000,INR
```

- UTF-8, with an optional UTF-8 BOM; exactly the three ordered headers above. Header-only inputs are valid; blank data rows are rejected.
- Quoted fields use doubled quotes for escapes. Quotes inside bare fields and whitespace after a closing quote are rejected.
- Each file is at most 5 MiB and 20,000 data rows.
- IDs are nonblank, at most 128 Java string characters, contain no control characters, and are compared exactly and case-sensitively. Surrounding spaces are preserved and affect matching.
- Amounts are decimal digits representing a strictly positive signed-64-bit integer (`1` through `9223372036854775807`). Zero, negative, fractional, overflowing, plus-prefixed, and space-padded amounts are rejected.
- Currency codes are uppercase ISO 4217 codes recognized by the Java runtime. No currency conversion occurs.
- An invalid row rejects the entire upload with HTTP 400; records are not silently skipped.
- Every distinct ID has one classification. A duplicate on either side takes precedence over other discrepancies and can never count as a match.

Classifications: `MATCHED`, `AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`, `AMOUNT_AND_CURRENCY_MISMATCH`, `MISSING_IN_PROCESSOR`, `UNEXPECTED_IN_PROCESSOR`, `DUPLICATE_INTERNAL`, `DUPLICATE_PROCESSOR`, `DUPLICATE_BOTH`.

## Design and limitations

The controller handles HTTP, a service coordinates parsing/matching and transactional persistence, the pure core owns validation/matching, and parameterized JDBC repositories own storage. Flyway manages schema changes. Matching uses hash indexes with expected O(n + m) work, followed by sorting for deterministic output. Runs, findings, and source occurrences are stored atomically; original duplicate rows remain available in the database.

See [architecture and tradeoffs](docs/architecture.md) and the [60–90 second demo](docs/demo.md).

Clerk authentication, CSRF protection, and per-user run isolation are implemented.
Clerk owns authentication sessions; JDBC sessions retain CSRF state. This remains a portfolio MVP, not a certified
production financial service: retention automation, distributed rate limiting,
application data deletion, live-provider HTTPS verification and operational
deployment controls remain release responsibilities. It compares payment
references, not bank payout IDs, and does not model fees, settlements, FX, refunds
or chargebacks. Uploads are synchronous and bounded in memory. Repeated POSTs
create new runs; no exactly-once or production compliance claim is made.
