# Production-readiness roadmap

This is the sequential plan from the 2026-10 production audit, not the older
`REFACTOR_PLAN.md`. Work is on `production-auth-migrations`, based on
`security-reliability-refactor`. Creating a branch from another branch does not
merge or publish either branch. No deployment is authorized by this plan.

## Stage 1 — schema, backup and safe foundation (P0-3 / P0-6)

Implemented locally on 2026-10-21: versioned V1 schema, a private dump of the
actual developer database, restoration into a separate database, all 16
application-table row counts and extended checksums compared, schema/sequence
verification against a separately migrated reference, clone adoption and full
isolated startup. The original was explicitly adopted at V1 without recreating
tables or changing application records. Flyway is now on by default and
Hibernate only validates; automatic baseline and clean remain disabled.

Files: `db/migration/mysql/`, `migration/`, application properties,
`mysql/*Test.java`, `scripts/Test-MySql.ps1`, `DATABASE_MIGRATIONS.md`.
Acceptance: empty and restored isolated MySQL startup, migration/adoption tests,
unchanged original data. Never edit an applied V1. Encrypt/off-host backup
automation remains Stage 7, not a claim made by this local restore rehearsal.

## Stage 2 — supported runtime and dependency baseline (P0-4 / Java advisory triage)

Completed and verified locally on 2026-10-21; no system MySQL upgrade. The owner
approved commit and publication to GitLab/GitHub on the same date, without a
merge or deployment. Remote pipeline results remain a separate verification.

Update to supported Spring Boot with compatible starters, Jackson/JPA/security
integration, Gradle and springdoc. Keep Java 21 and Node 24 LTS. Select MySQL
8.4.11 LTS for new production installs and disposable CI tests; do not replace
the developer's existing MySQL service as part of a source-code upgrade.

Files: Gradle/wrapper, framework imports/configuration, serialization/security
tests, MySQL tests, CI and runtime/setup documentation.
Depends on Stage 1. Acceptance: full regression suites, builds, fresh/restored
MySQL startup and repeated dependency review. Primary risk: changed framework
defaults; preserve HTTP/JSON contracts, account locking and existing behavior.
Details and verification checkpoint: `RUNTIME_UPGRADE.md`.

## Stage 3 — reliable authentication lifecycle (P0-1 / P0-2 / P1-5 / P1-8 / P1-9)

Approved and implemented locally on 2026-10-21, after the separately published
Stages 1–2 checkpoint. The new minimum is 10 characters by the owner's explicit
choice. Existing shorter credentials still work for sign-in. Shared password
validation, expected 400/409 account errors, ownership-based username checks
and Base64URL/UTF-8 JWT metadata decoding are covered by regression tests.
Earlier P0 logout and serialized-security safeguards are retained and retested.
See [AUTH_LIFECYCLE_STAGE3.md](AUTH_LIFECYCLE_STAGE3.md) for details and boundaries.
The owner approved commit and publication of Stage 3 on 2026-10-21.

Files: auth/security services/repositories, frontend AuthService/profile forms.
Acceptance: reset/revoke cannot restore old sessions, real MySQL overlapping
operations, negative API/frontend tests. Keep access lifetime 30 minutes and
remembered refresh lifetime 60 days. Risk: rejecting valid sessions or existing
users accidentally; cover these paths explicitly.

## Stage 4 — consistency and price transactions (P1-1 / P1-2 / P1-3)

Approved and implemented locally on 2026-10-21, after publishing Stage 3 as
`9ce96b1`. Atomic creation, tracked-only V2 uniqueness, current locking item
reloads and commit-before-cache-publication are covered by rollback/concurrency
tests. No V2 migration was applied to the real developer database. Details:
[DATA_CONSISTENCY_STAGE4.md](DATA_CONSISTENCY_STAGE4.md). The owner approved
commit and publication to GitLab/GitHub on 2026-10-21.

Make multi-step additions atomic; enforce only the correct uniqueness rules;
prevent lost updates and publish cache changes after successful commit.
Files: item/tracking/pricing services, repositories and new migrations.
Depends on the migration foundation. Verify rollback and concurrent operations
on MySQL, preserving valid repeated portfolio transactions. Do not edit V1.

## Stage 5 — frontend correctness and resilience

Approved and implemented locally on 2026-10-21, after publishing Stage 4 as
`5dd3983`. The existing interface and calculation-retention behavior are kept.
Source/FX metadata, true elapsed-time charts, recoverable failures, shared form
validation and keyboard controls are covered by regression tests and an isolated
browser walkthrough. Additive V3 was tested on disposable MySQL 8.0/8.4 only;
neither V2 nor V3 was applied to the real developer database by this stage.
Details: [FRONTEND_RELIABILITY_STAGE5.md](FRONTEND_RELIABILITY_STAGE5.md).
The owner approved Stage 5 commit and push on 2026-10-21. Merging and deployment
remain separate, unauthorized actions; remote verification is a separate gate.

P1-4 / P1-6 / P1-7 / P1-10 / P1-13, including date handling. Improve freshness
metadata, a real time axis, errors/retry, forms and keyboard accessibility.
Files: DTOs, chart utilities, forms and page hooks. Verify component tests and a
browser walkthrough. Preserve the connected line between available chart
points and calculator result retention during navigation.

## Stage 6 — limits and performance (P0-7 / P1-11)

Approved and implemented locally on 2026-10-21 after publishing Stage 5 as
`c516f6f` to GitLab and GitHub. Request-body and per-peer/account admission limits,
bounded Steam lookups/downloads, tracked insertion quotas, database pagination,
weighted inventory caching and route code splitting are in place. No additional
schema migration or real-database change was made. Verification and operating
boundaries: [RESOURCE_LIMITS_STAGE6.md](RESOURCE_LIMITS_STAGE6.md).
The owner approved committing and pushing Stage 6 to both remotes on 2026-10-21.

Add justified request limits, bounded Steam concurrency, quotas, pagination and
separate cache capacities. Files: request protection, inventory/tracking APIs,
frontend history loading. Verify controlled load, query counts, memory and
latency. Tune limits against real workflows; avoid arbitrary restrictions.

## Stage 7 — production configuration and operations (P0-5 / P1-12 / P1-14)

Fail-closed startup configuration, HTTPS/proxy/cookie contract, health/metrics,
audit retention, encrypted off-host automated backup and release/rollback
procedure. Files: production properties, security, CI/release/monitoring docs.
Verify a staging environment, headers/cookies, failure recovery and rollback.
Select infrastructure only when the hosting context is known; no Kubernetes or
microservices are implied. A Git rollback does not roll back MySQL DDL.

## Stage 8 — final acceptance

Browser E2E, MySQL regression, security checks, responsive/keyboard review and
current documentation against a specific release commit. All applicable gates
must pass before public release. Unit tests alone are not production readiness.

The owner approved Stage 5 publication, starting Stage 6, and subsequently
committing and pushing the verified Stage 6 changes on 2026-10-21.
Stages 7–8 remain unimplemented and require approval.
Merging branches and public deployment remain unauthorized.
