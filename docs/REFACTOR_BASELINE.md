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
