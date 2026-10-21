# Production-readiness implementation checkpoint

Updated 2026-10-21. Branch: `production-auth-migrations`, based on
`security-reliability-refactor`. The owner approved commit and publication of
these changes to GitLab/GitHub on 2026-10-21. No deployment, credential rotation
or merge is included. The sequential plan is
[PRODUCTION_PLAN.md](PRODUCTION_PLAN.md), not the historical refactor plan.

## Stage 1 — completed locally

- Versioned V1 SQL defines all 16 application tables, indexes, foreign keys,
  ID-generator tables and the market-price mutex seed. No user data/secrets.
- A real private dump of the developer `investment` database was made with
  consistent InnoDB snapshot options. The dump is about 15.5 MB and is ignored
  by Git under `backups/database/`; no export is uploaded.
- It was restored into a separate isolated MySQL database. All 16 table row
  counts and extended checksums matched the original. The file's SHA-256 was
  rechecked and the application-table fingerprints still matched after adoption.
- The restored schema and sequence state were compared with a separate fresh
  V1 reference, then explicitly adopted at version 1 and used for a complete
  application startup with outbound HTTP/mail mocked and jobs disabled/delayed.
- Only after that rehearsal was the original database explicitly baselined at
  V1. No application table was recreated and no application records reset.
- Default, `migrated` and `prod` profiles now use Flyway plus Hibernate
  `validate`, never automatic `update`. Unadopted non-empty databases fail
  safely. Automatic baseline and destructive clean remain disabled.

The local dump is **not encrypted** and must remain private. Store an encrypted
copy outside this computer before relying on it for disaster recovery. This
local verification is not an automated encrypted off-host production backup
policy; that operational work remains Stage 7. Test data is still sensitive
when it includes real account email addresses/hashes/sessions.

Procedure and maintenance tool: [DATABASE_MIGRATIONS.md](DATABASE_MIGRATIONS.md).
V1 is now applied/adopted and must not be edited; use a new migration instead.

## Stage 2 — completed locally: supported runtime foundation

Spring Boot 4.1.1, compatible modular starters/test packages, Gradle 8.14.6 with
distribution checksum, BOM-managed dependencies and springdoc 3.1.1. Jackson
3.1.8 / Jackson 2.21.8 and Tomcat 11.0.27 are aligned same-line security patches
newer than the Boot BOM. Java 21 and Node 24.20.0 remain unchanged.

MySQL 8.4.11 LTS is the selected new-install/CI target, tested using a separate
official portable server. The system MySQL 8.0.37 service is unchanged. A full
startup was also verified against the restored/adopted 8.0.37 clone using the
new framework versions. Changing the system service is a separate operation.

Regression checks preserve login defaults, ISO date/time JSON, numeric prices,
strict quantities/IDs and fresh role membership under Hibernate 7's changed
locking behavior. No provider cadence or financial logic was changed.

Details, advisory triage, commands and primary references:
[RUNTIME_UPGRADE.md](RUNTIME_UPGRADE.md).

## Earlier P0 authentication groundwork retained

- Reliable all-device sign-out with expired/missing access-token fallback via
  an active protected refresh cookie, idempotent cookie deletion, and a
  non-simple request requirement for cookie-authenticated refresh/logout.
- Account-first locking for login, refresh, reset/password changes, revocation
  and administrative security mutations. Refresh updates only last-use state
  conditionally, never saves a stale whole session that could clear revocation.
- Real MySQL tests cover stale managed security/role state, role addition and
  removal after a repeatable-read snapshot, pending writes, refresh/revoke and
  login/reset races, and independence of different accounts.

Access lifetime remains **30 minutes**; remembered refresh remains **60 days**.
Release frontend/backend auth changes together because cookie-authenticated
refresh/logout require the new header. See [AUTH_SECURITY.md](AUTH_SECURITY.md).
Remaining Stage 3 password-policy/API-error/JWT-decoding work is not claimed
complete merely because the earlier logout/concurrency groundwork exists.

## Local verification checkpoint (2026-10-21)

- Backend: 398 regular tests; 397 passed, one pre-existing manual measurement
  test disabled, zero failures/errors. `bootJar` succeeds.
- Isolated MySQL 8.4.11: all 13 migration/adoption, Hibernate validation, full
  startup and real InnoDB concurrency tests passed. Restored MySQL 8.0.37
  startup also succeeds. Both disposable servers have been stopped.
- Runtime dependency audit: 119 resolved Maven components checked in OSV;
  **zero advisory matches** after the documented patches. This is a timestamped
  dependency check, not a guarantee of zero vulnerabilities or a host/history scan.
- Frontend: ESLint, all 118 tests, production build and all 17 production HTTP
  smoke checks pass. Existing react-datepicker sourcemap and ~741 KB minified
  chunk warnings remain outside this checkpoint.
- GitHub workflow passes actionlint. Redacted backend/frontend source and
  GitHub-workflow scans found no secrets. Ignored local data and historical Git
  commits are not certified by these source-only scans.
- The pinned official MySQL Docker tag exists. GitHub/GitLab workflows run after
  publication; their actual remote results must be checked separately. These
  local verification results are not a green remote pipeline.

## Remaining gates

Do not publish yet. Continue Stages 3–8 only with approval: remaining auth policy,
data consistency, frontend correctness, limits/performance, fail-closed production
settings, HTTPS/proxy/cookies, least-privilege DB access, monitoring, encrypted
automated off-host backup/recovery, release/rollback and final browser acceptance.
No real SMTP delivery, live provider requests or public deployment was performed
by these verification checks.
