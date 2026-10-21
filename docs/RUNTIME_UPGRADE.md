# Supported runtime and dependency baseline

Production-roadmap Stage 2, local checkpoint 2026-10-21. No deployment or system
MySQL service replacement. See `PRODUCTION_PLAN.md` for scope and later gates.

## Selected baseline

| Component | Version / decision |
| --- | --- |
| Java | JDK 21 retained |
| Spring Boot | 4.1.1, stable artifact verified on Maven Central |
| Spring Framework / Security | Boot-managed 7.0.9 / 7.1.1 |
| Gradle wrapper | 8.14.6, official distribution SHA-256 recorded in wrapper properties |
| Dependency management plugin | 1.1.7 |
| springdoc | 3.1.1, compatible Boot 4.1 line |
| Hibernate ORM | Boot-managed 7.4.5.Final |
| Flyway / MySQL Connector/J | Boot-managed 12.4.0 / 9.7.0 |
| Jackson 3 BOM | 3.1.8 security patch override of Boot's 3.1.5 |
| Jackson 2 BOM | 2.21.8 security patch override of Boot's 2.21.5 |
| Embedded Tomcat | 11.0.27 security patch override of Boot's 11.0.24 |
| MySQL Server | 8.4.11 LTS for new installs / disposable CI; existing 8.0.37 retained locally |
| Node / React / Vite | 24.20.0 / 18.3.1 / 8.3.4 retained |

Use the wrapper, not a separately installed Gradle. Only patch releases within
the BOM's Jackson/Tomcat lines are overridden, with each Jackson family kept
aligned. Review/remove the overrides when a newer Boot BOM includes equivalent
patches. Other libraries (Lombok, Mockito, Caffeine, etc.) use compatible BOM
versions instead of stale individual overrides. JJWT remains 0.13.0.

MySQL 8.4.11 was tested from a separate official Windows portable distribution,
with vendor checksum and Oracle executable signature verified. It does not
upgrade or modify the machine's existing MySQL 8.0.37 service. A later system
server upgrade requires its own approved backup/restore maintenance window.
No portable tools or private data are tracked in Git.

## Compatibility work, not new business features

- Boot 4 modular MVC/Flyway/JPA test starters and test-package imports.
- Jackson 3 `tools.jackson` databind imports; `com.fasterxml.jackson.annotation`
  annotations intentionally retain their original namespace. Production uses
  Jackson 2-compatible defaults plus the existing strict fractional-ID/quantity
  protection. Date/date-time/instant JSON stays ISO text and prices stay numbers.
- Explicit login-request constructors preserve JSON request creation. Omitted
  `rememberMe` remains false; the public login contract is unchanged.
- JJWT and springdoc still require Jackson 2 internally. Its presence is not an
  accidental second MVC mapper; both Jackson families receive security patches.
- Hibernate 7 narrows default locking to root tables. Security reloads therefore
  flush pending changes, detach only the affected account and perform one
  shared-lock fetch of its current role membership. `INCLUDE_FETCHES` is explicit:
  a root-only locking read can retain an old InnoDB snapshot for joined tables.
  The account's exclusive lock stays held, shared roles are not exclusively
  locked, and portfolio collections stay uninitialized. All joined rows are
  consumed, not a short-circuited result stream. The Hibernate-specific scope
  is isolated here until a JPA 4 / Hibernate 8 migration supplies its replacement.
- Regular H2 tests explicitly generate their disposable schema; production SQL,
  schema validation and locking are verified separately by `mysqlTest`.

The V1 SQL was not changed after adoption. No account credentials, financial
calculation, provider cadence or user-interface workflow is changed by Stage 2.
The existing 30-minute access / 60-day remembered-session settings are retained.

## Repeatable dependency review

From `investment-tracker/`, with Java 21 and network access:

```powershell
.\gradlew.bat runtimeDependencyAudit
```

This resolves the actual `runtimeClasspath`, writes an inventory and queries
OSV in batches using **only public Maven names and versions**. No source,
application secrets or database records are transmitted. Reports remain in
ignored `build/reports/security/`. `runtimeDependencyInventory` works without
calling OSV when artifacts are already cached. The audit fails on findings,
unavailable/incomplete results or paginated results requiring manual follow-up;
it does not leave an older successful report marked current. Findings are not
automatically dismissed by exploitability assumptions.

Boot 4.1.1's unmodified BOM still matched 17 package/advisory pairs (10 distinct
advisories) during this review. Relevant conditions were checked before patching:

| Family | Conditions reviewed | Resolution |
| --- | --- | --- |
| Jackson core | DataInput error-token growth; string-number parsing ReDoS | Patch both core families via their BOMs |
| Jackson databind | Identity forward references; polymorphic type handling; XML datatype parsing; Path providers; unknown type-ID cache growth | Patch both families, including transitive Jackson 2 |
| Tomcat | DIGEST replay and container FORM/security-constraint bypass | Patch all embedded Tomcat modules together |

The application does not configure container DIGEST/FORM authentication,
`@JsonIdentityInfo`, unsafe polymorphic typing, JSON-bound `Path` or XML datatype
request fields. Those preconditions were not present in the inspected code;
that is not a reason to retain avoidable affected versions. The final repeat
scan must be read together with its timestamp and regression results. A clean
OSV result is not a guarantee of zero vulnerabilities and covers neither Git
history, host software nor frontend dependencies.

The repeat scan completed at 2026-10-21 10:15:45 UTC: 119 runtime components,
zero advisory matches. Final checks: 398 regular backend tests (397 passed,
one existing manual measurement disabled), 13/13 MySQL 8.4.11 tests, restored
MySQL 8.0.37 full startup, 118/118 frontend tests, both builds and 17/17 frontend
production HTTP smoke checks. Temporary test servers were stopped afterward.

## Verification and boundaries

Run backend `test bootJar runtimeDependencyAudit`, then isolated `mysqlTest`
using `scripts/Test-MySql.ps1` with explicit 8.4 binaries. A full application
startup test also runs against the separately restored/adopted 8.0.37 clone,
with outbound HTTP/mail mocked and background jobs disabled/delayed. Compare
the original application's table fingerprints after adoption. Frontend:
`npm.cmd run verify` (lint, tests, build, production HTTP smoke).

Security regression must cover stale managed account state, role additions and
removals after a repeatable-read snapshot, preserved pending writes, both
refresh/revoke orderings, password-reset/login races, non-blocking login of an
unrelated account and no portfolio loading on account-security locks. Do not
weaken these tests to accommodate a framework upgrade.

CI pins MySQL 8.4.11 (its official Docker tag was checked) and uses the new wrapper.
A local passing run is not evidence
that modified GitHub/GitLab workflows have run remotely. OSV review remains an
explicit command at this checkpoint, not a claimed scheduled monitoring system.
Real browser acceptance, live SMTP/providers, performance/load checks and
remaining production gates belong to later stages. Existing frontend warnings
about react-datepicker's upstream sourcemap and a ~741 KB minified chunk remain.

## Primary references

- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Official Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Gradle distribution checksums](https://gradle.org/release-checksums/)
- [springdoc compatibility](https://springdoc.org/)
- [MySQL supported upgrade paths](https://dev.mysql.com/doc/refman/8.4/en/upgrade-paths.html)
- [Hibernate locking scopes](https://docs.hibernate.org/orm/7.4/javadocs/org/hibernate/Locking.Scope.html)
- [Jackson core security advisories](https://github.com/FasterXML/jackson-core/security/advisories)
- [Jackson databind security advisories](https://github.com/FasterXML/jackson-databind/security/advisories)
- [Tomcat 11 security fixes](https://tomcat.apache.org/security-11.html)
- [OSV query API](https://google.github.io/osv.dev/api/)
