# Frontend design

## Authenticated dashboard extension — 2026-10-14

Login/signup and the protected run-history dashboard now reuse the existing
design language. The public landing retains its animation script and section
order, with a sign-in entry in place of anonymous uploads. The working report
UI moved to dashboard.html, and new identity/history rendering uses textContent.
See authentication.md for current security and verification evidence; earlier
verification entries below describe the pre-authentication design passes.

## Current visual reference

The latest reference is [Neutomni](https://neutomni.com): oversized red identity,
white space, editorial composition, and physical-document motifs. ReconcileX
adapts those principles to a payment-record desk, not an agency website.
`theme.css` uses a white canvas, crimson actions/display type, Satoshi typography,
warm-neutral paper surfaces, and squared corners. The original React Bits text
is preserved in `DESIGN.md`, followed by the current adaptation.

The masthead and hero are visually recomposed; section order, feature grid,
pinned-card sequence, carousel, and dashboard controls are retained.
`app.js` and `presentation.js` were not changed in this pass. Dashboard success,
warning, and error colors supplement explicit labels. Code and exact identifiers
retain a monospace face. No new framework or backend behavior was added.

The recruiter-facing introduction follows a cinematic hero, engineering showcase,
design decisions, live workspace, and closing call to action. The workspace retains
the existing API, exact amount strings, filters, pagination, detail dialog, and CSV
export. No frontend framework or additional server is required.

Presentation behavior is isolated in `presentation.js`. GSAP pinning and stacking
run only at desktop widths; mobile cards remain in normal document flow. System
reduced motion and the visible pause control disable motion. Content and controls
remain available if animation libraries fail to load.

## Locally served assets

- Phosphor Icons 2.1.1, regular weight: `assets/phosphor.svg` contains the nine used icons from `@phosphor-icons/core`. The MIT license is retained in `assets/phosphor-LICENSE.txt`. Decorative SVGs inherit text color and are hidden from assistive technology; carousel controls retain their accessible names. The custom ReconcileX brand mark is unchanged.
- Satoshi Regular and Bold: [Fontshare](https://www.fontshare.com/fonts/satoshi).
- Payment-report illustration: original local `assets/record-sheet.svg`, explicitly labeled illustrative; sample values demonstrate a match and an amount mismatch in minor units. The earlier landscape asset is no longer displayed.
- GSAP and ScrollTrigger 3.13.0: [GSAP](https://gsap.com/), locally vendored with license notices in the files.

All assets are served by Spring Boot under the existing self-only content security
policy. No runtime CDN access or uploaded transaction data is used for presentation.

## Verification

Run the dependency-free frontend checks with:

```sh
node --test backend/src/test/frontend/*.test.cjs
```

Desktop and 390 × 844 Safari responsive previews were inspected. Browser inspection
caught and corrected missing spaces when desktop line breaks are hidden on mobile.
The pause control was exercised. Automated presentation tests cover carousel wrap,
missing animation libraries, system reduced motion, and pause/resume teardown.
The live mixed-fixture upload returned 201; retrieval and export returned 200, with
all 10 distinct findings present. This is local verification, not a cross-browser
accessibility audit or a production deployment.

Final verification on 2026-10-13: 11 frontend tests passed. Maven `verify` with
`RECONCILEX_POSTGRES_TEST=true` and `-Dreconcilex.benchmark=true` passed all 46
Java tests with zero skips, failures, or errors. The final executable JAR was built.

## Reference-inspired revamp verification — 2026-10-14

- Frontend tests: 13 passed, zero failures.
- Maven verification with PostgreSQL and benchmark enabled: 46 tests passed,
  zero failures/errors/skips; executable JAR built.
- Live synthetic mixed-fixture upload: HTTP 201; GET and export: HTTP 200.
  Ten findings, including nine requiring review; formula-leading reference
  was apostrophe-prefixed in CSV export.
- Native Safari desktop and 390 × 844 responsive previews inspected. A saved
  report was opened through the actual UI; summary and findings rendered.
- Art direction used the frontend taste and existing-project redesign skills:
  editorial-report seed 1699979945. User reference and preserved structure take
  precedence over the generated section variants.
- The initial theme lint reported six flags: two retained section labels,
  two monospace declarations, and two `box-shadow: none` false positives.
  Explicit, explained exemptions retain the existing labels and app-data fonts
  and remove the old shadows. Final two-file lint: zero failures/warnings with
  these exemptions. This is not a claim that the whole legacy
  stylesheet satisfies every taste rule.
- The skill's automated browser inspector was not run: native Safari inspection
  was used to comply with the available computer-control workflow. This is not
  a comprehensive cross-browser or accessibility audit.

### Visual critique

The oversized identity and offset report/headline establish the reference's
editorial rhythm while keeping the subject unmistakably reconciliation.
The same paper motif continues through duplicate, export, and validation cards.
The mobile opening prioritizes readable copy and the real workspace action;
the large illustrative report is intentionally hidden below 960px. The existing
card-heavy engineering structure remains a deliberate constraint, not an exact
reproduction of Neutomni. Desktop engineering and pinned-card views were checked
for legibility. Remaining risk: browser/font differences and assistive-technology
behavior have not been exhaustively audited.
