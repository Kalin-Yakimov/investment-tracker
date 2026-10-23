# Refactoring baseline

Recorded: 2026-10-09 (Europe/Sofia).

## Starting point

- Base branch: `main`, clean working tree at the start of Stage 1.
- Base commit: `b4efc5282e5bbfe87b8db522482b975bd6596a9e`.
- Work branch: `security-reliability-refactor`.
- Baseline commit subject: `feat: preserve inventory results and improve chart tooltips`.
- At the starting checkpoint, only Stage 1 (preparation and data safety) was authorized.
- Publication and merging were not authorized at that checkpoint.

## Existing checks before the Stage 1 changes

| Check | Observed result |
|---|---|
| Backend tests | 56 tests in 15 suites: 55 passed, 1 skipped, 0 failures/errors |
| Frontend tests | 8 passed in 3 suites |
| Frontend production build | Successful |
| Working tree | Clean |

The backend numbers were checked in the generated Gradle XML reports, dated
2026-10-09, before changing the build or application configuration. The frontend
tests were repeated during Stage 1; frontend code and dependencies were unchanged.
The production build result comes from the immediately preceding project review.

The skipped backend test is `SteamPriceCacheMeasurement`, an explicitly manual
network measurement. It is not counted as a successful test or a cache benchmark.

Known warnings and limits:

- Frontend tests print the existing deprecated `ReactDOMTestUtils.act` warning
  from the testing library. It is not being fixed in Stage 1.
- The preceding dependency audit reported 82 affected-package findings
  (15 low, 16 moderate, 47 high, 4 critical), including development tooling.
  This is not evidence of 82 exploitable application vulnerabilities; dependency
  changes are reserved for their own stage.
- These tests do not establish production readiness, load capacity or full
  manual coverage of every user workflow.

## Toolchain observed locally

| Component | Observed version |
|---|---|
| Java | 21.0.3 |
| Gradle wrapper | 8.8 |
| Spring Boot | 3.5.16 |
| Node.js | 20.14.0 |
| React | 18.3.1 |

These are baseline versions, not new recommendations. Supported runtime and
dependency updates are handled in the later tooling stage.

## Data-safety boundary

- Stage 1 does not connect to, migrate, export or modify the real MySQL database.
- Existing local Hibernate `update` and production `validate` settings are kept.
- Flyway is installed but disabled by default; automatic baselining is disabled
  and its destructive `clean` operation is blocked.
- Migration tests use randomly named, disposable H2 in-memory databases, a
  test-only SQL fixture and no real database credentials.
- A backup helper and migration activation checklist are provided separately.
- No real backup or restore verification has been performed in Stage 1. Both
  are prerequisites before the first real database schema change.

See [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md) for the required safeguards.

## Checks after Stage 1 preparation

- Backend: 60 tests in 16 suites, 59 passed, the same 1 skipped, no failures or
  errors. Four new tests cover migration preparation; no existing test was removed.
- Frontend: the same 8 tests passed in 3 suites; no frontend changes were made.
- Backend application archive (`bootJar`): successfully built. Flyway Core and
  its MySQL adapter resolve to 11.7.2 through the Spring Boot dependency management.
- Archive inspection: test-only SQL and preparation tests are not packaged.
- Backup helper: PowerShell syntax and `-WhatIf` checks passed without a database
  connection or export. Real export and restore behavior remains to be verified
  before the first database change.
- Backup and partial-backup paths are ignored by Git; `git diff --check` passed.

## Separately approved Stage 2 checkpoint

Stage 2 was approved after the preparation checkpoint above. Its implementation,
policy defaults, deployment boundaries and verified test results are recorded in
[AUTH_SECURITY.md](AUTH_SECURITY.md). The base commit and Stage 1 results above
remain historical reference points; they are not the current test totals.

## Separately approved Stage 3 checkpoint

Stage 3 API validation, type-scoped ownership checks, portfolio transaction
boundaries and verified results are recorded in [API_VALIDATION.md](API_VALIDATION.md).
Earlier stage totals above are retained as historical checkpoints.

## Separately approved Stage 4 checkpoint

Stage 4 external HTTP limits, price-publication/cache transaction boundaries,
background-job guards and catalogue synchronization are documented in
[EXTERNAL_SERVICES.md](EXTERNAL_SERVICES.md). This stage does not change the
database schema or frontend. Earlier totals remain historical checkpoints.

## Separately approved Stage 5 checkpoint

Stage 5 public Steam inventory loading, stack quantities, unavailable-price
handling and canonical-profile caching are documented in
[INVENTORY_CALCULATOR.md](INVENTORY_CALCULATOR.md). Database schema and real
MySQL data are unchanged. Frontend changes are limited to valuation correctness
and discarding legacy browser results once. Earlier totals remain historical
checkpoints; publication/merging is a separate user decision.

## Separately approved Stage 6 checkpoint

Stage 6 frontend request ownership, cancellation, stale-response/session guards,
bounded history loading and non-overlapping Support Panel polling are recorded
in [FRONTEND_REQUEST_LIFECYCLE.md](FRONTEND_REQUEST_LIFECYCLE.md). The UI design,
existing cache policies, backend and database schema are unchanged by this stage.
Earlier totals remain historical checkpoints. Commit, push and merge remain
separate user decisions.

## Separately approved daily-history correction

Following the Stage 6 manual review, daily history now uses one row per
item/source/UTC publication day, with latest-publication replacement, unchanged
next-day observations, newest-first log display and stored-date protection after
restart. Unlike Stages 5/6, this separately approved correction includes a
targeted MySQL schema/data adoption after a verified private backup and temporary-
table restore rehearsal. Details and verification are recorded in
[DAILY_PRICE_HISTORY.md](DAILY_PRICE_HISTORY.md). No commit, push or merge was
performed as part of this correction.

## Separately approved Stage 7 checkpoint

Stage 7 page/component extraction, shared price-chart presentation, isolated
frontend HTTP/domain services, separated backend price responsibilities and
unused-cache cleanup are recorded in
[STRUCTURAL_REFACTOR.md](STRUCTURAL_REFACTOR.md). Backend checks: 295 tests,
294 passed and the same 1 skipped; frontend checks: 84 passed. Both application
builds succeeded. Database schema/data, external API routes, price-publication
policy and UI styling are unchanged. Manual review and publication/merging
remain separate steps. The remaining sequence is saved in
[REFACTOR_PLAN.md](REFACTOR_PLAN.md).

## Separately approved organization and DRY follow-up

After the initial Stage 7 commit, feature folders and clearer class/file names
were approved, together with a review of duplicated code. Backend checks:
299 tests in 35 suites, 298 passed and the same 1 skipped. Frontend checks:
89 tests in 21 suites, all passed. Both application builds succeeded.
The skipped measurement is now named `PriceFeedCacheMeasurement`; its manual,
external-network nature is unchanged. Earlier names and totals above describe
the corresponding historical checkpoints.

Shared table navigation, price-period selection, response/error handling and
portfolio request mapping were extracted without changing API routes, JSON
fields, entity/table names, daily-history rules, cache settings or UI styles.
See [CODEBASE_STRUCTURE.md](CODEBASE_STRUCTURE.md) for the current organization.
This follow-up does not start Stage 8. Commit/push were separately approved on
2026-10-12; merge remains a separate user decision.

## Separately approved backend reuse follow-up (2026-10-12)

The additional backend DRY review is recorded in
[BACKEND_REUSE.md](BACKEND_REUSE.md). Backend checks: 342 tests in 39 suites,
341 passed and the same 1 skipped, no failures/errors; `test bootJar` succeeded.
43 new regression cases cover shared pagination, cookie settings, error-response
contracts, support-access recording, trusted catalogue metadata and Retry-After
parsing. Frontend/resource/entity hashes are unchanged from this follow-up's
starting point. Earlier checkpoints remain historical. Publication/merge and
Stage 8 are still separate user decisions.

## Separately approved Stage 8 checkpoint (2026-10-12)

Vite/Vitest migration and compatible frontend dependency/tool updates are in
[FRONTEND_TOOLING.md](FRONTEND_TOOLING.md). Verified using Node.js 24.19.0:
94 frontend tests in 22 files, all passed; lint has 0 errors/warnings; production
build and 18 local HTTP smoke checks passed. All 89 existing frontend test cases,
14 source stylesheets and 63 production module bodies are retained, excluding
imports and the two explicitly migrated public environment accesses. Backend
files and real MySQL data are unchanged by this stage.

The current npm audit reports 0 findings (previously 82, including development
tooling). This is not a production-security certification. A bundle-size warning
remains; at the initial migration checkpoint, system Node.js 20.14.0 had not yet
been updated for manual startup.
The new lock file was verified through a clean `npm ci`; publication/merge and
Stage 9 are separate decisions.

## Separately approved system runtime update (2026-10-12)

System Node.js was upgraded from 20.14.0 to 24.20.0 LTS using the standard
Windows package manager and the official Node.js MSI, whose hash was verified.
The old global npm override was upgraded to npm 11.19.0, matching the new
installer's bundled version. Commands resolve through the normal installation,
not an alternate runtime path. Source code, backend/database settings and real
data are unchanged by this installation. No commit, push or merge was performed.
Clean `npm ci` and `npm run verify` passed using these normal system tools:
94 tests in 22 files, lint with no warnings/errors, successful production build,
no lock-file change and the same 0 npm audit findings.

## Separately approved Stage 9 checkpoint (2026-10-12)

Final automated verification, integrated workflows, GitLab CI configuration and
browser acceptance boundaries are recorded in [FINAL_VERIFICATION.md](FINAL_VERIFICATION.md).
Frontend: 108 tests in 23 files, all passed, lint has no warnings/errors,
production build and 17 production HTTP smoke checks succeeded. Backend:
358 tests in 40 suites, 357 passed and the same 1 skipped, no failures/errors;
`test bootJar` succeeded without personal application secrets in the test
process. Existing tests remain; 16 backend workflow and 14 frontend route cases
were added. Earlier checkpoints remain historical.

No real MySQL/provider/SMTP operations, changes to production business logic or
styling, commit, push or merge were performed in this stage. The actual GitLab
runner/pipeline and manual browser checklist remain pending; no production
readiness or full browser E2E claim is made.

## Browser date-picker regression follow-up (2026-10-12)

The subsequent user browser check found the blank portfolio caused by the
`react-datepicker` UMD browser entry selected by Vite. An exact-match ESM alias,
a real client-resolution test (failing before the fix) and an unmocked calendar
form test correct that tooling boundary. Frontend checks now pass 110 tests in
25 files with clean lint, successful build and 17 HTTP smoke checks. Backend
was unchanged; its preceding 357 passed / 1 skipped checkpoint remains valid.
Targeted Chrome navigation verified the existing-session portfolio, its date
picker, item details and edit date picker, without saving or deleting records.
The original automated checkpoint above remains historical; full manual
acceptance, GitLab execution and publication/merge remain pending.

## Stable catalogue dropdown follow-up (2026-10-12)

Following user approval, valid search edits retain previous suggestions during
debounce/loading, with latest-response replacement and unchanged cancellation
guards. Clear, selection, short input, empty results and current failures clear
them. The shared Portfolio/PriceTracker hook is the only production-source change;
the 1000 ms default, backend and CSS remain unchanged. Six hook cases and one
unmocked form/dropdown identity case were added. All 117 frontend tests in 26
files, lint, build and 17 HTTP smoke checks pass. A separate temporary Chrome tab
verified the dropdown staying visible through a keystroke, refreshing results
and closing on clear, without record submissions. Publication/merge remain pending.

## Generic catalogue relevance follow-up (2026-10-12)

The separately approved improvement replaces substring/candidate-cutoff ranking
with bounded word-prefix matching and database ordering before the eight-result
limit. Generic name normalization, repository fragment and cache validation are
documented in [CATALOGUE_SEARCH.md](CATALOGUE_SEARCH.md). The old specification,
unused search query and Java relevance scorer were removed. API/DTO contracts,
entity/schema mappings, internal metadata lookup and UI/cache timings remain.

34 new regression cases pass. Full backend `test bootJar`: 392 tests in 43
suites, 391 passed and the existing one skipped, no failures/errors. The actual
production SQL and parameters were additionally checked in a read-only local
MySQL 8.0.37 transaction against 44,635 catalogue rows for nine query examples.
No catalogue/portfolio writes, migration or app restart were performed. Frontend
code was unchanged by this follow-up, so its preceding 117-test checkpoint remains
historical. Browser acceptance after backend restart and publication/merge remain
separate user decisions; earlier checkpoints above are not overwritten.
