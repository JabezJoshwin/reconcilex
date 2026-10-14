# Clerk authentication and private history

ReconcileX now uses Clerk, not direct Google OAuth. ClerkJS Core 3 supplies signup,
login and logout in the existing vanilla-JavaScript pages. Spring verifies each
short-lived Clerk session JWT with Nimbus/Spring Security against the configured
instance's cached JWKS. No React application, password database or OAuth client
secret is added.

## Configure locally

1. Create an application at https://dashboard.clerk.com/. Enable email/password
   or email-code sign-in and require email verification. Optional social methods
   are configured in Clerk, not in Spring.
2. From Clerk's API keys page copy the **Publishable key** and **Frontend API URL**.
   Keep the issuer exactly the HTTPS origin, with no trailing slash or path.
3. Add these values to the ignored root `.env`, preserving database settings:

```dotenv
CLERK_PUBLISHABLE_KEY=pk_test_your_publishable_key
CLERK_ISSUER=https://your-instance.clerk.accounts.dev
APP_BASE_URL=http://localhost:18080
```

Use your actual app port everywhere. The default Compose port is 8080; this
checkout's local demo uses 18080. The issuer must match the domain encoded in the
publishable key. These two values are public configuration; no Clerk secret key
is needed for this implementation. Never put an `sk_test_` or `sk_live_` key in
frontend code or chat. Old GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET settings are ignored.

4. Restart the native Java app after sourcing `.env`, or rebuild/recreate Compose
   with `docker compose up -d --build`.
5. Visit `/signup`, complete Clerk verification, then check `/dashboard`.
   Upload clean/mixed fixtures, refresh history, reopen, export and log out.
   Configure Clerk's allowed application origins/redirects to your actual origin.
   No Google callback URL is needed by ReconcileX.

Missing configuration leaves the public site and health endpoint usable, but
login is disabled with a clear setup message. Production startup refuses missing
configuration, non-HTTPS origins, and test-instance publishable keys.

## Security boundaries

- Verify RS256 signature, exact issuer, expiry, not-before, issued-at, exact
  authorized party (APP_BASE_URL), user subject and session ID on every request.
  Pending sessions are rejected. Tokens longer than 16 KiB or with a lifetime
  over two minutes are rejected. Keep Clerk's default short session tokens.
- APIs use fresh `Authorization: Bearer` tokens obtained with ClerkJS getToken().
  Page navigation can use Clerk's `__session` cookie. No app token is stored in
  localStorage/sessionStorage. An expired cookie does not block the public login
  page, allowing the SDK to refresh; private APIs remain fail-closed.
- The local account key is (Clerk issuer, Clerk user ID), never email. All
  reconciliation/history/export queries remain owner-scoped. Legacy Google
  sessions cannot authenticate, even if their Spring session is still stored.
- Spring JDBC sessions now hold CSRF state only, not authenticated identity.
  Unsafe API requests and local logout require CSRF plus same-origin protection.
- Logout clears the local CSRF session and calls Clerk.signOut(). Offline JWT
  validation does not instantly revoke a previously copied token: it remains
  usable until expiry (normally about a minute, at most the two-minute cap).
  Requiring immediate server-side revocation would need online session checks.
- The server stores only a local ID and stable Clerk identity, not provider
  passwords or profile data. `/api/me` returns a fallback display name and blank
  email for new Clerk accounts; the dashboard displays the profile from ClerkJS
  using textContent. Payment files and reports remain in local PostgreSQL.
- CSP allows the exact configured Clerk instance and Clerk's bot-protection
  domains. Clerk UI requires inline styles; inline scripts/eval remain blocked.
  ClerkJS/UI load from the instance CDN, version lines 6 and 1 respectively.
  Review CDN updates and live CSP behavior before public deployment.

## Existing data

No runs or users are deleted by this change. Old Google-owned runs are not
automatically reassigned to Clerk accounts, even when emails match. Previously
unowned runs remain inaccessible. The existing explicit local administrative
script can assign only unowned legacy runs:

```sh
sh scripts/assign-legacy-runs.sh YOUR_CLERK_LOCAL_USER_UUID --preview
# Back up and verify the account/count before applying:
sh scripts/assign-legacy-runs.sh YOUR_CLERK_LOCAL_USER_UUID --apply
```

Get the new local UUID from authenticated `/api/me`. Migrating already-owned
Google runs requires a separately reviewed administrative data migration; this
implementation deliberately does not guess account ownership.

## Production release gates

Use a live Clerk instance, an HTTPS APP_BASE_URL and production profile.
Configure Clerk's domain/DNS and enabled methods, MFA/session policy and account
recovery according to the deployment's needs. Restrict proxy trust, keep secrets
in the deployment secret store, add edge/distributed rate limits, backups,
retention/deletion policies and monitoring before exposing financial data.

Real Clerk signup, email verification, session refresh, CAPTCHA, logout and
production HTTPS must be exercised with your configured instance. Local signed
JWT tests and mocked ClerkJS tests do not substitute for those checks. No live
Clerk credentials were available during implementation.

## Verification

Migration checks executed locally: Maven verify passed 59 Java tests with zero
failures/errors/skips, including PostgreSQL and the opt-in benchmark; Node passed
24 frontend tests. An initial origin-check regression was fixed and the complete
suite rerun. These results verify local application code, not a live Clerk account.

Run frontend tests:

```sh
node --test backend/src/test/frontend/*.test.cjs
```

Run Java tests/build as in the root README; opt into PostgreSQL verification with
RECONCILEX_POSTGRES_TEST=true and your local DB_URL/password. ClerkFlowTest signs
real RSA JWTs and verifies signatures/claims, ownership, upload, history, reload,
safe CSV export, CSRF/origin failures, legacy-session rejection and expired-cookie
recovery. These fixtures never bypass authentication in the shipped app.
