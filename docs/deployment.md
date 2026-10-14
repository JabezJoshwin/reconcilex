# Free-tier deployment and CI/CD

## Intended hosting

Render **Free** Docker web service + Neon **Free** PostgreSQL 17, both Singapore.
Use a fresh Neon project/database, not a copy of local data. Clerk remains the
existing development instance. This is a synthetic-data portfolio demo, not
production financial hosting. Existing production-mode requirements remain intact.

Free allowances can change. Verify both plans before creating resources. Do not
add a card or enable automatic paid upgrades. Render's verified workspace was
Hobby with no card on file. If quotas run out, stop/suspend rather than upgrade.
Render sleeps after 15 minutes idle and may take about a minute to start again.
Neon also suspends idle compute. There are no keep-alive pings, paid disks, cron
services or added monitoring subscriptions.

## Provision and configure

1. Create Neon project `reconcilex-demo`, AWS Singapore, PostgreSQL 17, database
   `reconcilex`. Enable only Postgres; keep Auth, Functions, AI and Storage off.
2. Create the Render service from root `render.yaml` in the existing GitHub repo.
   Confirm `plan: free`, Singapore, Docker, branch main, auto-deploy **off**.
   Do not apply the blueprint to any unrelated existing service.
3. Supply the following runtime values in Render, not in source/GitHub logs:

| Setting | Value |
| --- | --- |
| SPRING_PROFILES_ACTIVE | demo |
| SERVER_ADDRESS | 0.0.0.0 |
| APP_BASE_URL | Exact generated `https://...onrender.com` origin, no slash |
| CLERK_PUBLISHABLE_KEY | Existing development publishable key |
| CLERK_ISSUER | Exact matching Clerk HTTPS Frontend API origin |
| DB_USER / DB_PASSWORD | New Neon role's credentials |
| DB_URL | JDBC URL described below |

Use the direct Neon database hostname in a JDBC URL without embedded credentials:

```text
jdbc:postgresql://NEON_HOST/reconcilex?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=10&socketTimeout=20
```

`DefaultJavaSSLFactory` uses Java's trusted CA store; `verify-full` requires
certificate/hostname validation. Do not downgrade to non-validating SSL. Keep the
default Java trust store current. Flyway applies existing migrations on startup;
no admin HTTP endpoint or local-data import is used.

4. Configure Clerk paths/origins for the Render URL, retaining current methods.
   Leave its development notice visible. Sign-in/verification is user-controlled.
5. In GitHub create environment `demo`; add environment secret `RENDER_API_KEY`.
   New credential/access creation needs explicit user confirmation. Never paste
   the key into chat. Add repository variables `RENDER_SERVICE_ID`, `APP_URL`, and
   `DEPLOY_ENABLED=true` only after the service/configuration is ready.
6. Push to main or manually run **Verify and Deploy**. Missing DEPLOY_ENABLED
   intentionally skips deployment during bootstrap; green CI alone is not a
   completed release. Deployment must be enabled and succeed for completion.

## Pipeline guarantees

PRs run Java/JUnit, actual disposable PostgreSQL, benchmark assertions, Node tests,
and a Linux AMD64 Docker build/smoke test. They receive no deployment secrets.
Main runs those same gates before requesting the exact verified SHA from Render.
GitHub serializes deployments; in-progress deployments are never cancelled.

The deployment script polls Render for at most 20 minutes, rejects terminal/API
failures, verifies Render's commit and `/api/health` revision, then checks ready,
static pages/assets, Clerk resources, secure CSRF cookies, HSTS, login redirects,
and anonymous rejection of private APIs. Reports/JARs are retained for seven days.

Render's recurring health path is `/api/health` (liveness without database queries).
`/api/ready` checks PostgreSQL and returns generic 503 if unavailable. Readiness is
checked during release, not by a scheduled keep-alive. Pool size is three with
zero minimum idle connections; the runtime heap is capped at 256 MiB.

Hosted writes validate against configured APP_BASE_URL; redirects use that same
origin. The demo ignores forwarding headers and always sets secure cookies/HSTS.
It does not trust arbitrary proxy headers for client identity. Demo uploads allow
ten attempts per account per minute and two concurrent submissions. Counters are
bounded and expire, but reset on restart and are not distributed; error responses
are 429 RATE_LIMITED or 503 BUSY with Retry-After. Original CSV limits still apply.

## Verification and rollback

```sh
node --test backend/src/test/frontend/*.test.cjs scripts/deploy/*.test.cjs
# Use your local test database credentials, never hosted credentials, for Maven tests.
RECONCILEX_POSTGRES_TEST=true ./backend/mvnw -f backend/pom.xml -Dreconcilex.benchmark=true verify
docker build --platform linux/amd64 -t reconcilex:ci backend
sh scripts/deploy/container-smoke.sh
```

The smoke script uses randomly named disposable containers and deletes only those
containers/network on exit. It never touches the Compose demo database volume.

Initial hosted acceptance still requires real browser login, upload of clean/mixed
fixtures, history/reload/reopen/export/logout, second-account isolation, persistence
after restart/redeploy, and an idle cold start. Automated signed-JWT tests or a
green health check alone do not prove this live user story. Log actual outcomes.

For rollback, manually run **Rollback demo** on main and provide a full SHA of a
previous successful deployed revision. It checks that GitHub recorded a successful
deploy job for that commit, redeploys it, then runs hosted checks. It never reverses
Flyway migrations; future schema changes must stay backward-compatible or require
a separately reviewed database recovery procedure.

## Current release evidence

Executed locally on 2026-10-14:

- Maven verification with real PostgreSQL and the benchmark enabled: 66 tests,
  zero failures, errors or skips; packaged JAR successfully.
- Frontend and deployment-script verification: 29 Node tests passed.
- Both GitHub workflows passed actionlint 1.7.12; all deployment YAML parsed.
- Linux AMD64 Docker build is still being verified; no hosted result is claimed.

Provisioning and hosted acceptance are not yet complete. Update this section only
with executed checks and the real public URL/deployment/workflow links. Do not
describe the app as deployed merely because deployment configuration exists.
