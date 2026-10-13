# Verification evidence

Executed locally on 2026-10-13. Java 21.0.1, Maven wrapper 3.9.11, macOS 26.6.2,
Apple M1, eight reported processors. The application image also compiled and tested on
Temurin Java 21 in Docker. PostgreSQL uses the `postgres:17-alpine` image.

## Automated checks

The final complete check was run from the repository root:

```sh
set -a
source .env
set +a
RECONCILEX_POSTGRES_TEST=true \
  DB_URL=jdbc:postgresql://localhost:55432/reconcilex \
  MAVEN_USER_HOME=/private/tmp/reconcilex-wrapper \
  ./backend/mvnw -B -ntp -Dmaven.repo.local=/private/tmp/reconcilex-m2 \
  -f backend/pom.xml -Dreconcilex.benchmark=true package
```

Result: **BUILD SUCCESS — 43 tests, zero failures, zero errors, zero skips.**
Breakdown: 30 parser cases, five matcher tests, six H2 API tests, one PostgreSQL
workflow test, one opt-in synthetic benchmark. The temporary Maven locations accommodate
the local sandbox; normal development uses `./mvnw test` / `./mvnw package` in `backend/`.
Without opt-in flags the PostgreSQL and benchmark checks skip intentionally.

API checks cover all categories, saved summaries/findings, filtered and bounded pages,
positive long precision as JSON strings, invalid/oversized uploads, header-only input,
formula escaping, deterministic repeat classifications, strict quoting and rollback after
a database failure occurring after the run/finding inserts.

`node --check backend/src/main/resources/static/app.js` passed. The frontend agent also
reported passing mocked-DOM checks for contract handling, malicious IDs as text, exact
64-bit rendering, pagination and export links. `git diff --check` passed.

## Live application and Docker evidence

`docker compose up --build -d` built the application and started both containers.
The Docker build ran its default suite successfully (PostgreSQL opt-in test skipped).
`docker compose ps` showed the app running and database healthy. Host ports in the
ignored local `.env` are `APP_PORT=18080` and `DB_PORT=55432` because the default ports
were occupied by existing services. Compose defaults remain 8080 and 5432 for other users.
No existing services were stopped or changed.

Observed real HTTP checks:

| Check | Observed result |
|---|---|
| `/api/health` | 200, `{"status":"UP"}` |
| Clean CSV upload | 201, three matches, zero discrepancies |
| Mixed CSV upload | 201, 11 internal rows, 10 processor rows, 10 IDs; all nine classifications |
| Saved GET / filtered GET | 200; duplicate-both count one, null ambiguous scalars, occurrences 2/2 |
| Export | 200; ten findings plus header; formula ID starts with apostrophe |
| Invalid amount | 400 |
| Real multipart file of 5 MiB + 1 byte | 400 with stable `INVALID_INPUT` JSON |

Safari verification submitted both mixed fixtures through the upload form. The UI visibly
reported completion, one match, nine discrepancies, and all ten findings. The Docker-hosted
dashboard retrieved that same persisted run and its amount-mismatch filter displayed the
single expected row (120000 vs 119900 INR). Desktop layout was visually inspected.
Mobile layout rules were inspected in CSS; no mobile-device visual test was executed.
CSV content and download response headers were verified over HTTP; browser download-to-disk
was not separately exercised. The verification skill guided boundary checks from UI to
API to database to response.

## Measured benchmark

10,000 rows per source; processor order reversed; 9,000 exact matches and 1,000 amount
mismatches. Three warmups then five measured parses/matches using `System.nanoTime`.
Input generation and assertions are outside timing; sorting is included. Heap ceiling
2048 MiB. Final full-suite measured milliseconds:

`29.861625, 25.746709, 21.289875, 17.524166, 17.705250`; median **21.290 ms**.

An earlier standalone invocation measured median 18.694 ms. Variation is expected.
This diagnostic excludes database, HTTP and export, is not JMH, and carries no SLA.

## Failures investigated and resolved

- Maven was absent; installed the official Maven distribution temporarily and generated
  a checked-in Maven wrapper. Dependency resolution required network approval.
- Default local Maven cache writes were sandbox-blocked; used a temporary cache.
- Docker daemon was initially stopped; started Docker Desktop.
- PostgreSQL port 5432 and application port 8080 were occupied; introduced host-port overrides.
- Mockito self-attachment failed in the sandbox. Configured its Java agent explicitly in Surefire;
  all affected tests subsequently passed. No tests were disabled to fix this.
- Fresh read-only reviewer reproduced permissive Commons CSV malformed quoting. Added a strict
  quote-structure pass and regression cases; full tests passed. Reviewer found no confirmed high
  priority issues in the other reviewed application areas.

## Remaining boundaries

The remote GitHub Actions workflow has not run. Its PostgreSQL service and Java 21 test
command mirror locally executed checks. No authentication, authorization, tenancy, operational
retention or production compliance is included. Currency validation depends on the installed
JDK's ISO registry. No unresolved functional failure is known in the tested MVP paths.
