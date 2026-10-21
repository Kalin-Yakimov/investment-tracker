# Controlled MySQL migrations

## Current implementation

`V1__initial_schema.sql` defines all 16 application tables (including ID-generator
tables), indexes, foreign keys and the market-price mutex seed. It contains no
account data, catalogue export or secrets. Fresh installation is tested against
real MySQL together with Hibernate schema validation and real InnoDB auth locks.

| Profile | Schema management |
| --- | --- |
| Default local | Flyway on, Hibernate `validate` |
| `migrated` | Compatibility alias for the same safe defaults |
| `prod` | Flyway on, Hibernate `validate`, plus production security settings |

Do not start any profile on an unadopted non-empty database. Flyway deliberately
refuses it. Keep `baseline-on-migrate=false`,
`clean-disabled=true`, checksum validation and strict migration naming.

On 2026-10-21 the developer database completed a real dump, separate restore,
comparison of all 16 application tables (row counts and extended checksums),
V1 schema comparison, clone adoption and full isolated application startup.
The original database was then explicitly baselined at V1 without recreating
tables or changing application records. A private dump remains under ignored
`backups/database/`. This checkpoint is not an automated encrypted off-host
backup policy and does not certify a different installation.

## Safe local MySQL verification

From the repository root, using MySQL 8.4.11 binaries and Java 21:

```powershell
.\scripts\Test-MySql.ps1 -MySqlBin 'C:\Program Files\MySQL\MySQL Server 8.4\bin'
```

This initializes a disposable MySQL data directory under ignored
`investment-tracker/build/mysql-tests/<unique-run>`, binds only to
`127.0.0.1:13307` and runs `mysqlTest`. It refuses an occupied port, never reuses
the system MySQL service, never uses `DB_PASSWORD` and stops only its own server.
Its temporary root account has no password and uses synthetic test data; this setup is
strictly for local tests, never deployment. Logs/data remain for diagnostics.
Override `-MySqlBin` or `-Port` if necessary. If script execution is blocked,
use an approved terminal/script policy; do not weaken machine-wide policy.

CI pins a disposable MySQL 8.4.11 service. Its explicit test-only credentials are
not application secrets. `mysqlTest` rejects URLs outside the allowed hosts or
database names outside `investment_test_*`; it never falls back to `DB_URL`.
The regular H2 `test` task does not run these MySQL tests.

## Maintenance tool (no application startup)

`databaseMigration` starts only the JDBC/Flyway maintenance tool. It does not
start Spring, catalogue synchronization, scheduled jobs, email or API clients.
Connection secrets must be supplied locally, never committed or placed in shell
arguments. Variables are separate from the running application's configuration:

- `MIGRATION_DB_URL`: explicit `jdbc:mysql://host:port/database` target.
  Optional URL settings are limited to `sslMode=VERIFY_IDENTITY`, `VERIFY_CA` or
  `REQUIRED`, and UTC `connectionTimeZone`/`serverTimezone`. Credentials in URLs
  and arbitrary connection options are refused; use verified TLS for remote DBs.
- `MIGRATION_DB_USERNAME`, `MIGRATION_DB_PASSWORD`: maintenance credentials.
- `MIGRATION_REFERENCE_URL`: separate disposable database created with the
  same shipped migrations; needed by `verify` and `adopt`.
- `MIGRATION_REFERENCE_USERNAME`, `MIGRATION_REFERENCE_PASSWORD`: optional
  separate reference credentials (otherwise the target credentials are used).
- `MIGRATION_BACKUP_RESTORE_VERIFIED=true`: operator acknowledgment required
  only for `adopt`. This is not itself proof that recovery works.

The database name must also be confirmed with `-PmigrationDatabase=<name>`.
The default action is read-only `verify`, and the default name is invalid.
The tool never runs `clean`, `repair` or automatic baseline.

### New empty installation

1. Create an empty UTF-8 MySQL database with `utf8mb4_0900_ai_ci` and a dedicated
   migration account. Use a separate restricted runtime account for the app.
2. Set the maintenance variables to that empty database.
3. From `investment-tracker`, run:

```powershell
.\gradlew.bat databaseMigration -PmigrationAction=migrate -PmigrationDatabase=investment
```

4. Start with the default profile locally, or `prod` only when all
   remaining production safeguards have been configured. Set the application's
   `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` to that same database.

The migration tool applies the schema only; initial roles are created by the
application's normal role initializer, not by hardcoded account/role IDs.

### Existing installation: backup, restore, verify, adopt

1. Stop **all** app instances before the migration window. This also stops
   schedulers and changes to pooled ID counters. Keep the source commit/release.
2. Create a private backup using Workbench Data Export or the existing helper:

```powershell
.\scripts\Backup-LocalDatabase.ps1 -Username investment_app
```

Use the real local username, such as `root` if still applicable. The helper
prompts for the password and writes a unique dump under ignored
`backups/database`. `-WhatIf` previews without connecting. A dump contains
emails, password hashes, sessions and user data: protect/encrypt it, restrict
access and never upload it. `.partial` exports are failures, not backups.

3. Restore the dump into a **separate** database, e.g. `investment_restore_check`.
   Compare table row counts and important relationships with the original.
   A file checksum or successful export alone does not verify recoverability.
   Do not start the full app just to inspect the clone: initializers/jobs write.
4. Build another empty disposable reference database using `migrate` above.
5. Point `MIGRATION_DB_URL` to the restored clone and
   `MIGRATION_REFERENCE_URL` to the reference. Run:

```powershell
.\gradlew.bat databaseMigration -PmigrationAction=verify -PmigrationDatabase=investment_restore_check
```

`verify` reads schema metadata: table engines, columns/types/nullability,
collations, primary/unique/query indexes, foreign keys and delete rules. It also
checks one-row pooled ID generators are ahead of existing IDs and the market
mutex seed exists. Generated constraint names and enum declaration order may
differ; application tables and semantics must match. It does not print user
records or credentials. Extra old columns/tables cause a refusal, not a deletion.
This check does not replace a data-integrity review or a restore rehearsal.

6. If verification fails, stop and review a non-destructive alignment on the
   clone. Do not drop data or baseline anyway. Review old one-off SQL files too:
   do not rerun historical cleanup scripts blindly.
7. After successful verification and recovery checks, set the acknowledgment
   and run `adopt` on the clone. It rechecks the schema and records baseline
   version 1 only. It does not recreate tables or reset ID generators.

```powershell
.\gradlew.bat databaseMigration -PmigrationAction=adopt -PmigrationDatabase=investment_restore_check
```

8. Test the migrated application against the clone in an isolated environment.
   Only then repeat the verification and explicit adoption on the original
   during the approved maintenance window, with all writers stopped and the
   verified backup retained. Keep Flyway and Hibernate `validate` enabled.
   Do not restart an older compatibility `update` release against a migrated DB.

`adopt` refuses an existing history marker. It is not a re-baseline operation.
`migrate` on non-empty unadopted databases still fails safely.

## Release and recovery rules

Once V1 is applied or adopted, never edit it. Add increasing migrations, e.g.
`V2__add_token_indexes.sql`, and test both fresh and upgrade paths on real MySQL.
Run migration validation and database tests before each release. Use a separate
migration credential where the runtime account cannot change the schema.

MySQL multi-statement DDL is not an all-or-nothing transaction. On failure, stop
deployment, inspect the actual state and recover the verified backup or apply a
reviewed forward repair. Never use blind `repair`/`clean`. A Git rollback alone
does not undo database changes. Prefer backward-compatible expand/contract
changes when old/new releases may overlap.

## References

- [Flyway baseline safety](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)
- [Flyway explicit baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline)
- [MySQL mysqldump](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)
