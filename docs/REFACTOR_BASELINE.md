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
