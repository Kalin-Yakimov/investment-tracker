# Pre-release checklist

This is a release gate, not a claim that unchecked infrastructure exists.
Source preparation: Stages 7–8, 2026-10-22. Public deployment remains unauthorized.
Each operational box needs an owner, timestamp, target and release commit.

Local candidate evidence: [FINAL_ACCEPTANCE_STAGE8.md](FINAL_ACCEPTANCE_STAGE8.md).
Stage 8 is published as `cbecc30` with successful GitHub Actions. This does not
mark the release gates below complete: no production release or hosting
acceptance has been performed; current GitLab CI remains unverified. Later
commits need their own verification. See [CURRENT_STATUS.md](CURRENT_STATUS.md).

## Code and artifacts

- [ ] Approved clean release commit; CI verified on that exact revision on both remotes.
- [ ] Backend regular/MySQL tests, frontend verify and runtime dependency audit pass.
- [ ] Stage 8 browser E2E, negative authorization and cross-account regression accepted.
- [ ] Backend, standalone migrations JAR and explicit HTTPS frontend built from the same commit.
- [ ] Release manifest lists commit, Java/Node/MySQL versions and SHA-256 of all artifacts.
- [ ] No secrets, SQL dumps, real user records or local environment files in the release.

## Host, network and configuration

- [ ] Supported patched Linux/Java 21/MySQL 8.4; reviewed non-root service and memory budget.
- [ ] Domain ownership, DNS, valid TLS, automatic renewal and expiry alert tested.
- [ ] Public access only through HTTPS proxy; Java/management/DB ports inaccessible externally.
- [ ] Actual proxy overwrites forwarded headers; source-IP budgets cannot be reset by spoofed XFF.
- [ ] Actual API, HTML, assets and error responses have expected CSP/security/cache headers.
- [ ] Catalogue/Steam images, dynamic chunks, charts and modal styles work under CSP.
- [ ] Private runtime secrets are generated, backed up securely and excluded from Git/logs.
- [ ] Separate restricted runtime, migration and backup credentials; remote DB identity-verified TLS.
- [ ] `prod` fails before DB access for unsafe settings; no persistent admin-email bootstrap.
- [ ] Initial ADMIN identity verified/provisioned once; ordinary users cannot access Support Panel.

## Data and recovery

- [ ] Current database adoption/migration state reviewed; no blind baseline/repair.
- [ ] Upgrade rehearsed on isolated restored data; duplicate tracking preflight reviewed.
- [ ] Off-host encrypted restic repository initialized/permissions reviewed; offline recovery key secured.
- [ ] Daily backup and weekly check timers actually run successfully and failures alert.
- [ ] Last-success age monitored independently of the application; no silently stale backup.
- [ ] Concrete snapshot restored and compared in isolated MySQL; measured RPO/RTO accepted.
- [ ] Restored auth sessions revoked before exposing any recovered database to users.
- [ ] Scoped retention policy reviewed with dry-run and independent protected copy before enabling deletion.

## Monitoring, user flows and rollout

- [ ] Private health/Prometheus endpoints work; public actuator/Swagger/env/heapdump denied.
- [ ] Readiness detects DB unavailability; provider outage does not trigger liveness restarts.
- [ ] Alert recipient receives test alerts for outage, failed jobs, stale prices/FX and backup failure.
- [ ] Restricted JSON/audit log collection, retention/cap and off-host critical-event storage configured.
- [ ] Real project SMTP verification/reset arrives; Spam/deliverability and recovery instructions checked.
- [ ] Secure/HttpOnly/Strict host-only cookies; refresh, expiry and all-device logout work in HTTPS browser.
- [ ] Real price/FX source availability/freshness inspected without fabricating missing historical dates.
- [ ] Browser navigation, paginated tracking, portfolio writes and inventory result retention accepted.
- [ ] Graceful SIGTERM/in-flight write handling and safe restart rehearsed.
- [ ] Previous artifacts retained; schema-compatible binary rollback or isolated restore/forward-fix rehearsed.
- [ ] Hosting-specific settings and operator responsibilities documented and signed off.

Procedures: [PRODUCTION_OPERATIONS.md](PRODUCTION_OPERATIONS.md).
