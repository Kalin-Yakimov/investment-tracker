# Final verification (Stage 9)

Updated: 2026-10-12. Branch: `security-reliability-refactor`.

The automated verification and CI configuration are implemented locally. Browser
acceptance, a real GitLab pipeline and publication/merge are separate remaining
steps. This document does not claim production readiness or completed manual QA.

## Changes in this stage

- Add backend `workflow/ApplicationWorkflowIntegrationTest` using the full Spring
  application, real security filters, JWT validation, BCrypt, controllers,
  services, JPA repositories and committed transactions. No test-user security
  shortcut or mocked authentication service is used.
- Use a disposable H2 database in MySQL compatibility mode. Explicit test
  properties override database/mail/JWT/bootstrap settings. Outbound HTTP,
  catalogue synchronization, price-feed access and SMTP are replaced by mocks;
  test executors run synchronously. An after-test assertion prohibits use of
  the outbound HTTP mock. Background refresh is delayed and cleanup crons are
  disabled. Real MySQL is neither connected to nor modified.
- Add frontend `AppRoutes.test.jsx` with real route composition, Navbar, route
  guards, AuthService state and subscriptions. Feature-page contents are test
  adapters, already covered by their feature tests. HTTP logout and initial
  session restoration are controlled in these UI tests. This is not browser E2E
  coverage and does not use real accounts.
- Add `scripts/smoke-build.js` and `npm run smoke:build`. It serves existing
  production output on a temporary loopback port, checks public files and SPA
  fallback URLs, then closes its own server. It does not run the React app or
  start the backend. `npm run verify` now runs lint, tests, build and this smoke
  check. Normal `npm start`, port 3000 and application behavior remain unchanged.
- Add root `.gitlab-ci.yml` with independent frontend/backend verification jobs
  and JUnit reports. Neither production source/styles nor database schema nor
  dependencies are changed in this stage.

## Integrated workflows covered

Backend checks exercise these contracts through MockMvc and the actual security
chain, not just direct method calls:

- anonymous requests cannot access portfolio, tracked items or support, or
  submit a contact request;
- USER, SUPPORT and ADMIN permissions differ correctly, including admin-only
  audit and support-role changes;
- registration, single-use email activation and real password authentication;
- portfolio create/update/list/delete with cross-user denials and preservation
  of the shared catalogue row;
- tracked-item ownership, pinning and separation from portfolio routes;
- two daily publications with an unchanged price create two observations;
  replaying the same publication creates no duplicate;
- contact request NEW → IN_PROGRESS → RESOLVED → removal by support, including
  new-count changes and denials for ordinary users;
- support reads require a reason and cannot inspect administrator accounts;
- granting/removing support revokes previous sessions and takes effect after
  a fresh login;
- sign-out invalidates access and refresh credentials on both simulated devices;
- refresh rotation followed by replay invalidates the affected session;
- account locking and password changes invalidate prior credentials/sessions;
- support maintenance jobs expose completion in the overview;
- configured-origin CORS preflight succeeds; an untrusted origin is denied.

Frontend route tests cover anonymous/private navigation, waiting for session
restoration, profile-menu permissions, role/session changes while on a private
screen, public registration and sign-out navigation. Existing feature tests
continue covering cancellation, stale responses, inventory-session ownership,
pagination, pinned items, charts and support polling.

## Local verification checkpoint

Toolchain: Java 21.0.3, Gradle wrapper 8.8, system Node.js 24.20.0 and npm 11.19.0.

- Frontend: 117 tests in 26 files, all passed after the browser date-picker
  and stable-search corrections below; lint has no errors or warnings. The initial Stage 9
  checkpoint had 108 tests in 23 files.
- Production frontend build succeeded. The production HTTP smoke passed all
  17 checks, including nine client routes, JavaScript/CSS and five public assets.
- Backend: 392 tests in 43 suites; 391 passed, the existing manual network
  measurement skipped, no failures/errors. `test bootJar` succeeded without
  personal database, mail or JWT environment settings in the test process.
  The initial Stage 9 checkpoint had 358 tests in 40 suites.
- The 16 new backend workflow cases, 14 frontend route cases and two date-picker
  regression cases add coverage, plus seven stable-dropdown cases and 34 backend
  catalogue matching/ranking/cache cases; no existing test is removed or disabled.
- npm audit reports 0 known dependency findings at this checkpoint, including
  development tools. This does not certify application security.
- The backend archive contains no workflow-test classes, H2 implementation,
  test fixtures, `.env` or backup entries. Generated reports remain ignored by
  Git. Whitespace checks passed.
- The original Stage 9 verification did not change production backend source,
  resources or frontend styling. The later approved catalogue-search behavior
  changes backend catalogue source only, as described below. Test fixtures,
  verification scripts, package scripts, CI configuration, ignore rules and docs
  are the Stage 9 changes; catalogue search is a separate approved follow-up.

H2 compatibility mode is not proof of every MySQL-specific behavior. Tests with
controlled providers do not validate live Gmail delivery, current Steam limits,
fresh CSGOTrader data, production HTTPS/cookies or real browser layout. The
skipped measurement is not counted as a passed benchmark.

After using the date picker's ESM entry, the main frontend JavaScript chunk is
approximately 741 kB minified / 224 kB gzip (previously 847 kB / 255 kB).
Vite's 500 kB warning is retained; lazy-loading/bundle optimization is not
silently included in this verification stage. It is not a failed build.

## Browser date-picker correction (2026-10-12)

The user's manual check revealed a blank authenticated portfolio screen.
Chrome console errors identified an invalid React component type at the real
`RegisterPortfolioItem` date picker. Vite's client resolver selected
`react-datepicker` 7.3.0's legacy UMD `browser` entry, exposing a module object
instead of the expected default component. Existing route tests used feature
adapters and Vitest's Node package resolution differs from the browser; the
earlier successful tests/build did not establish that this form worked.

An exact-match Vite alias now selects the package's already installed
`dist/es/index.js` entry for dev, build and tests. Stylesheet imports, library
version, form source, backend/API behavior and data schema are unchanged.
The new real-client resolver test failed against the UMD entry before the fix
and passes afterward. A second regression renders/opens the actual date picker
in the registration form without mocking the library.

All 110 frontend tests, lint, production build and 17 HTTP smoke checks passed.
In the existing Chrome session, the authenticated portfolio, its calendar,
item details and edit-form calendar were checked successfully; navigation was
returned to the portfolio. No portfolio save/delete or account/role action was
submitted. This is targeted browser coverage, not completion of the full manual
checklist. No commit/push/merge was performed.

The library's ESM sourcemap references missing upstream source files in Vitest.
This packaging warning does not prevent rendering/tests/build and is not hidden
by disabling sourcemaps or changing the library's files.

## Stable catalogue dropdown follow-up (2026-10-12)

The shared `useCatalogueSearch` no longer clears successful suggestions at every
valid keystroke. The dropdown stays mounted during the unchanged 1000 ms default
debounce and the next request; only the current response replaces its rows.
Clear/selection/too-short input clears the list and cancels pending work.
An empty result or a current non-cancellation failure clears previous suggestions
instead of leaving them indefinitely. Cancellation and late-response guards
remain unchanged. Portfolio registration and PriceTracker use the same hook;
no new cache, request source, backend change or styling change is introduced.

Six hook regression cases and one real portfolio-form DOM-identity test cover
retention, replacement, late replies, clearing/selection, empty results and
failures. All 117 frontend tests, lint, build and 17 HTTP smoke checks pass.
In a separate temporary Chrome tab, the dropdown remained visible immediately
after typing from `Re` to `Rev`, updated its rows and disappeared after clearing.
The temporary tab was closed; no portfolio/price-tracker record was submitted.
Full manual acceptance and GitLab execution remain pending.

## Generic catalogue relevance follow-up (2026-10-12)

Following user approval, all typed terms now match word prefixes in one catalogue
name, with generic handling of case, separators and hyphenated identifiers.
The database orders all matches before returning eight results, replacing the
previous unordered 40-candidate cutoff and Java ranking. API/DTO contracts,
catalogue entities, provider access, frontend debounce and cache TTLs are
unchanged. The obsolete search specification and unused repository query were
removed. Details and examples are in [CATALOGUE_SEARCH.md](CATALOGUE_SEARCH.md).

34 new backend cases pass: 13 normalization/limits, 17 real repository-query
cases on isolated H2 fixtures and four actual cache/proxy cases with mocked
persistence/HTTP. The full `test bootJar` checkpoint passes 391 tests with one
unchanged skipped manual measurement (392 total, 43 suites). Frontend source was
not changed or re-tested by this backend-only follow-up; its preceding 117-test,
lint/build/smoke checkpoint above remains historical evidence.

The actual production SQL and bound parameters were also checked through JDBC
in a read-only transaction on local MySQL 8.0.37, with 44,635 catalogue rows.
Nine different searches completed with relevant results; no database writes,
migrations or application startup were performed. This is SQL compatibility and
targeted result verification, not real Spring/MySQL browser E2E or load testing.
The existing running backend was not restarted; the user must restart it to load
the new code and clear its previous search cache before browser acceptance.
No commit/push/merge was performed.

## GitLab CI behavior and safety

The pipeline runs on branch pushes and merge requests; duplicate branch-push
pipelines are suppressed when an MR pipeline is applicable. It has no deploy,
database service, real SMTP account, production credential or migration job.
Both jobs require a Linux Docker-compatible runner:

- frontend: official `node:24.20.0-bookworm-slim`, locked `npm ci`, lint, Vitest
  with JUnit, production build and production HTTP smoke;
- backend: official `eclipse-temurin:21-jdk-jammy` and the repository's Gradle
  wrapper, running `test bootJar` with the existing deterministic test suite.

The container tags were confirmed available at this checkpoint. YAML syntax and
local job/report/no-deployment structure assertions passed using the existing
SnakeYAML parser. Commands and JUnit files were checked locally, but the actual
Linux runner/pipeline has not
been executed or certified by these Windows checks. After an approved push,
review **Build → Pipelines** in GitLab and use its CI Lint/Pipeline Editor if a
runner/configuration issue is reported. Runner access and compute quota depend
on the GitLab account/project settings; no settings were changed here.

Only test/coverage reports are retained as job artifacts for seven days.
No `.env`, database export, backups, full workspace or application binaries are
uploaded. The frontend receives only the two known public settings by default.
Do not add personal mail/database/JWT credentials to these jobs or put secrets
under `VITE_`. Report output for these tests contains only synthetic fixtures.

## Remaining acceptance and delivery steps

1. Complete [the manual browser checklist](MANUAL_VERIFICATION.md), preferably
   using disposable non-administrator accounts for role/account actions.
2. Record failures with the page, steps and visible error; correct and recheck
   only the affected behavior before acceptance.
3. Publication of all pending changes to `security-reliability-refactor` was
   approved on 2026-10-12. Inspect the first real GitLab pipeline after push;
   do not call it passed before it runs.
4. Merge only after acceptance and explicit user approval. A merge is not a
   production deployment or permission to change the real database schema.

## Primary references

- [GitLab CI YAML reference](https://docs.gitlab.com/ci/yaml/)
- [GitLab unit-test reports](https://docs.gitlab.com/ci/testing/unit_test_reports/)
- [GitLab job artifacts](https://docs.gitlab.com/ci/jobs/job_artifacts/)
- [Vite JavaScript API](https://vite.dev/guide/api-javascript)
